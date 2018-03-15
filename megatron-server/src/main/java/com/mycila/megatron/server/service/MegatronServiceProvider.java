/*
 * Copyright © 2017 Mathieu Carbou (mathieu.carbou@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycila.megatron.server.service;

import com.mycila.megatron.DisoveringMegatronPlugins;
import com.mycila.megatron.MegatronApi;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.MegatronEventListener;
import com.mycila.megatron.MegatronPlugin;
import com.mycila.megatron.server.config.DefaultMegatronConfiguration;
import com.tc.classloader.BuiltinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.entity.PlatformConfiguration;
import org.terracotta.entity.ServiceConfiguration;
import org.terracotta.entity.ServiceProvider;
import org.terracotta.entity.ServiceProviderConfiguration;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.monitoring.PlatformService;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Mathieu Carbou
 */
@BuiltinService
public class MegatronServiceProvider implements ServiceProvider, Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MegatronServiceProvider.class);

  private static final Collection<Class<?>> providedServiceTypes = Arrays.asList(
      MegatronConfiguration.class,
      MegatronEventListener.class
  );

  private static final AtomicLong schedulerCounter = new AtomicLong();
  private static final AtomicLong executerCounter = new AtomicLong();

  private final ScheduledExecutorService scheduledExecutorService = Executors.unconfigurableScheduledExecutorService(new ScheduledThreadPoolExecutor(
      Runtime.getRuntime().availableProcessors(),
      r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        t.setName("MegatronScheduler-" + schedulerCounter.incrementAndGet());
        t.setUncaughtExceptionHandler((thread, err) -> LOGGER.error("UncaughtException in thread " + thread.getName() + ": " + err.getMessage(), err));
        return t;
      },
      new ThreadPoolExecutor.AbortPolicy()
  ));

  private final ExecutorService executorService = Executors.unconfigurableExecutorService(new ThreadPoolExecutor(
      0, Math.min(Integer.MAX_VALUE / 100, Runtime.getRuntime().availableProcessors()) * 100,
      10, TimeUnit.SECONDS,
      new SynchronousQueue<>(),
      r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        t.setName("MegatronScheduler-" + schedulerCounter.incrementAndGet());
        t.setUncaughtExceptionHandler((thread, err) -> LOGGER.error("UncaughtException in thread " + thread.getName() + ": " + err.getMessage(), err));
        return t;
      },
      new ThreadPoolExecutor.AbortPolicy()
  ));

  private final DisoveringMegatronPlugins plugins = new DisoveringMegatronPlugins();
  private final DefaultMegatronConfiguration megatronConfiguration = new DefaultMegatronConfiguration();

  private PlatformConfiguration platformConfiguration;
  private CompletableFuture<MegatronApi> api = new CompletableFuture<>();

  public MegatronServiceProvider() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  @Override
  public Collection<Class<?>> getProvidedServiceTypes() {
    return providedServiceTypes;
  }

  @Override
  public void prepareForSynchronization() {
    api = new CompletableFuture<>();
  }

  @Override
  public boolean initialize(ServiceProviderConfiguration configuration, PlatformConfiguration platformConfiguration) {
    this.platformConfiguration = platformConfiguration;

    Collection<DefaultMegatronConfiguration> configurations = platformConfiguration.getExtendedConfiguration(DefaultMegatronConfiguration.class);
    configurations.forEach(megatronConfiguration::merge);

    Collection<MegatronPlugin> list = plugins.getPlugins();
    LOGGER.info("Plugins found: {}{}",
        list.size(),
        list.isEmpty() ? "" : list.stream().map(p -> "\n - " + p.getClass().getName()).collect(Collectors.joining()));

    LOGGER.info("init({})", megatronConfiguration);
    plugins.init(megatronConfiguration);

    api.thenAccept(plugins::setApi);

    return true;
  }

  @Override
  public void close() {
    LOGGER.info("Closing...");
    try {
      plugins.close();
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    scheduledExecutorService.shutdown();
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
        LOGGER.error("Some tasks did not terminated within 10 seconds");
      }
      if (!scheduledExecutorService.awaitTermination(20, TimeUnit.SECONDS)) {
        LOGGER.error("Some tasks did not terminated within 10 seconds");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void addStateTo(StateDumpCollector dump) {
    dump.addState("plugins", plugins.getPlugins());
  }

  @Override
  public synchronized <T> T getService(long consumerID, ServiceConfiguration<T> configuration) {
    Class<T> serviceType = configuration.getServiceType();

    // for platform, which requests either a IStripeMonitoring to send platform events or a IStripeMonitoring to send callbacks from passive entities
    if (MegatronConfiguration.class == serviceType) {
      return serviceType.cast(megatronConfiguration);
    }

    if (MegatronEventListener.class == serviceType && configuration instanceof MegatronServiceConfiguration) {
      ManagementService managementService = ((MegatronServiceConfiguration) configuration).getManagementService();
      PlatformService platformService = ((MegatronServiceConfiguration) configuration).getPlatformService();
      api.complete(new DefaultMegatronApi(managementService, platformService, platformConfiguration.getServerName(), executorService, scheduledExecutorService));
      return serviceType.cast(plugins);
    }

    throw new IllegalStateException("Unable to provide service " + serviceType.getName() + " to consumerID: " + consumerID);
  }

}
