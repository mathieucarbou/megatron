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
package com.mycila.megatron;

import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.Collection;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Mathieu Carbou
 */
public class DisoveringMegatronPlugins implements MegatronPlugin {

  private final Collection<MegatronPlugin> plugins = new CopyOnWriteArrayList<>();

  public DisoveringMegatronPlugins() {
    this(Thread.currentThread().getContextClassLoader());
  }

  public DisoveringMegatronPlugins(ClassLoader classLoader) {
    ServiceLoader.load(MegatronPlugin.class, classLoader).forEach(plugins::add);
  }

  public void add(MegatronPlugin plugin) {
    plugins.add(plugin);
  }

  public boolean remove(MegatronPlugin plugin) {
    return plugins.remove(plugin);
  }

  public Collection<MegatronPlugin> getPlugins() {
    return plugins;
  }

  @Override
  public void setApi(MegatronApi api) {
    plugins.forEach(p -> p.setApi(api));
  }

  @Override
  public void onNotification(ContextualNotification notification) {
    plugins.stream()
        .filter(MegatronPlugin::isEnable)
        .forEach(plugin -> plugin.onNotification(notification));
  }

  @Override
  public void onStatistics(ContextualStatistics statistics) {
    plugins.stream()
        .filter(MegatronPlugin::isEnable)
        .forEach(plugin -> plugin.onStatistics(statistics));
  }

  @Override
  public void init(MegatronConfiguration configuration) throws ConfigurationException {
    ConfigurationException configurationException = new ConfigurationException("Megatron configuration failed for some plugins, they will be disabled. See stacktrace for more information.");
    for (MegatronPlugin plugin : plugins) {
      try {
        plugin.init(configuration);
      } catch (RuntimeException e) {
        configurationException.addSuppressed(e);
      }
    }
    if (configurationException.getSuppressed().length > 0) {
      throw configurationException;
    }
  }

  @Override
  public boolean isEnable() {
    return plugins.stream().allMatch(MegatronPlugin::isEnable);
  }

  @Override
  public boolean isInitialized() {
    return plugins.stream().allMatch(MegatronPlugin::isInitialized);
  }

  @Override
  public void close() {
    plugins.forEach(MegatronPlugin::close);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DisoveringMegatronPlugins{");
    sb.append("plugins=").append(plugins);
    sb.append('}');
    return sb.toString();
  }

}
