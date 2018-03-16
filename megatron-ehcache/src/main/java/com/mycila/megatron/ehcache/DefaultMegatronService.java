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
package com.mycila.megatron.ehcache;

import com.mycila.megatron.ConfigurationException;
import com.mycila.megatron.DisoveringMegatronPlugins;
import com.mycila.megatron.MegatronApi;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.Utils;
import org.ehcache.Cache;
import org.ehcache.Status;
import org.ehcache.core.events.CacheManagerListener;
import org.ehcache.core.spi.service.CacheManagerProviderService;
import org.ehcache.core.spi.service.ExecutionService;
import org.ehcache.core.spi.store.InternalCacheManager;
import org.ehcache.core.spi.time.TimeSourceService;
import org.ehcache.impl.internal.util.ThreadFactoryUtil;
import org.ehcache.management.CollectorService;
import org.ehcache.management.ManagementRegistryService;
import org.ehcache.management.registry.DefaultCollectorService;
import org.ehcache.spi.service.Service;
import org.ehcache.spi.service.ServiceDependencies;
import org.ehcache.spi.service.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.management.model.call.Parameter;
import org.terracotta.management.model.cluster.Client;
import org.terracotta.management.model.cluster.ClientIdentifier;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@ServiceDependencies({CacheManagerProviderService.class, ExecutionService.class, TimeSourceService.class, ManagementRegistryService.class})
public class DefaultMegatronService implements MegatronService, CacheManagerListener, CollectorService.Collector {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMegatronService.class);

  private final MegatronServiceConfiguration configuration;

  private volatile DisoveringMegatronPlugins plugins;
  private volatile ManagementRegistryService managementRegistryService;
  private volatile CollectorService collectorService;
  private volatile InternalCacheManager cacheManager;
  private volatile MegatronApi megatronApi;
  private volatile String cacheManagerAlias;
  private volatile Context context;

  public DefaultMegatronService(MegatronServiceConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public MegatronConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public void start(ServiceProvider<Service> serviceProvider) {
    this.managementRegistryService = serviceProvider.getService(ManagementRegistryService.class);
    this.cacheManager = serviceProvider.getService(CacheManagerProviderService.class).getCacheManager();

    this.collectorService = new DefaultCollectorService(this);
    this.collectorService.start(serviceProvider);

    context = managementRegistryService.getConfiguration().getContext()
        .with(Client.KEY, ClientIdentifier.create("Ehcache:" + cacheManagerAlias, Utils.generateShortUUID()).getClientId());
    cacheManagerAlias = context.get("cacheManagerName");
    ExecutorService executorService = serviceProvider.getService(ExecutionService.class).getUnorderedExecutor("megatron-scheduler", new SynchronousQueue<>());
    ScheduledExecutorService scheduledExecutorService = serviceProvider.getService(ExecutionService.class).getScheduledExecutor("megatron-task");
    ThreadFactory threadFactory = ThreadFactoryUtil.threadFactory("megatron");
    megatronApi = new EhcacheMegatronApi(cacheManagerAlias, executorService, scheduledExecutorService, threadFactory, configuration);

    this.cacheManager.registerListener(this);
  }

  @Override
  public void stop() {
    if(collectorService != null) {
      collectorService.stop();
    }

    if (plugins != null) {
      plugins.close();
      plugins = null;
      managementRegistryService = null;
    }
  }

  @Override
  public void cacheAdded(String alias, Cache<?, ?> cache) {
  }

  @Override
  public void cacheRemoved(String alias, Cache<?, ?> cache) {
  }

  @Override
  public void stateTransition(Status from, Status to) {
    // we are only interested when cache manager is initializing (but at the end of the initialization)
    switch (to) {

      case AVAILABLE: {
        DisoveringMegatronPlugins megatronPlugins = createMegatronPlugins();

        LOGGER.info("[{}] Initializing Megatron with config: {}", cacheManagerAlias, configuration);
        try {
          megatronPlugins.init(configuration);
        } catch (ConfigurationException e) {
          LOGGER.warn(e.getMessage(), e);
        } finally {
          this.plugins = megatronPlugins;
        }

        LOGGER.info("[{}] Collecting statistics each {}ms", cacheManagerAlias, configuration.getStatisticCollectorInterval());
        try {
          managementRegistryService.withCapability("StatisticCollectorCapability")
              .call(
                  "startStatisticCollector",
                  Void.TYPE,
                  new Parameter(configuration.getStatisticCollectorInterval(), long.class.getName()),
                  new Parameter(TimeUnit.MILLISECONDS, TimeUnit.class.getName()))
              .on(context)
              .build()
              .execute()
              .getResult(context)
              .getValue();
        } catch (NoSuchElementException e) {
          LOGGER.warn("[{}] Unable to start statistic collector: no statistic collector found in management registry.", cacheManagerAlias);
        } catch (ExecutionException e) {
          LOGGER.warn("[{}] Unable to start statistic collector: {}", cacheManagerAlias, e.getCause().getMessage(), e.getCause());
        }
        break;
      }

      case UNINITIALIZED: {
        this.cacheManager.deregisterListener(this);
        break;
      }

      case MAINTENANCE:
        // in case we need management capabilities in maintenance mode
        break;

      default:
        throw new AssertionError("Unsupported state: " + to);
    }
  }

  private DisoveringMegatronPlugins createMegatronPlugins() {
    DisoveringMegatronPlugins plugins = new DisoveringMegatronPlugins();
    plugins.setApi(megatronApi);
    return plugins;
  }

  @Override
  public void onNotification(ContextualNotification notification) {
    DisoveringMegatronPlugins plugins = this.plugins;
    if (plugins != null && notification != null) {
      LOGGER.trace("[{}] onNotification({})", cacheManagerAlias, notification.getType());
      notification.setContext(context.with(notification.getContext()));
      plugins.onNotifications(Collections.singletonList(notification));
    }
  }

  @Override
  public void onStatistics(Collection<ContextualStatistics> statistics) {
    DisoveringMegatronPlugins plugins = this.plugins;
    if (plugins != null && !statistics.isEmpty()) {
      LOGGER.trace("[{}] onStatistics({})", cacheManagerAlias, statistics.size());
      for (ContextualStatistics statistic : statistics) {
        statistic.setContext(context.with(statistic.getContext()));
      }
      plugins.onStatistics(statistics instanceof List ? (List<ContextualStatistics>) statistics : new ArrayList<>(statistics));
    }
  }

}
