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
import org.terracotta.entity.ServiceProviderCleanupException;
import org.terracotta.entity.ServiceProviderConfiguration;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.monitoring.PlatformService;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collection;
import java.util.ServiceLoader;
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

  private final MegatronPlugins plugins = new MegatronPlugins();

  private final DefaultMegatronConfiguration megatronConfiguration = new DefaultMegatronConfiguration();
  private PlatformConfiguration platformConfiguration;

  public MegatronServiceProvider() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  @Override
  public Collection<Class<?>> getProvidedServiceTypes() {
    return providedServiceTypes;
  }

  @Override
  public void prepareForSynchronization() throws ServiceProviderCleanupException {
  }

  @Override
  public boolean initialize(ServiceProviderConfiguration configuration, PlatformConfiguration platformConfiguration) {
    this.platformConfiguration = platformConfiguration;
    Collection<DefaultMegatronConfiguration> configurations = platformConfiguration.getExtendedConfiguration(DefaultMegatronConfiguration.class);
    configurations.forEach(megatronConfiguration::merge);
    ServiceLoader.load(MegatronPlugin.class, MegatronPlugin.class.getClassLoader()).forEach(plugins::add);

    Collection<MegatronPlugin> list = plugins.getPlugins();
    LOGGER.info("Plugins found: {}{}",
        list.size(),
        list.isEmpty() ? "" : list.stream().map(p -> "\n - " + p.getClass().getName()).collect(Collectors.joining()));

    return true;
  }

  @Override
  public void close() {
    plugins.getPlugins().forEach(MegatronPlugin::close);
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
      LOGGER.info("init({}, {})", consumerID, megatronConfiguration);
      plugins.init(megatronConfiguration, managementService, platformService, platformConfiguration.getServerName());
      return serviceType.cast(plugins);
    }

    throw new IllegalStateException("Unable to provide service " + serviceType.getName() + " to consumerID: " + consumerID);
  }
}
