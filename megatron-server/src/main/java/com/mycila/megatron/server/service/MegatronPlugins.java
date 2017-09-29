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
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.monitoring.PlatformService;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mathieu Carbou
 */
class MegatronPlugins implements MegatronEventListener {

  private final Collection<MegatronPlugin> plugins = new ArrayList<>();

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

  void add(MegatronPlugin plugin) {
    plugins.add(plugin);
  }

  Collection<MegatronPlugin> getPlugins() {
    return plugins;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("MegatronPlugins{");
    sb.append("plugins=").append(plugins);
    sb.append('}');
    return sb.toString();
  }

  void init(MegatronConfiguration configuration, ManagementService managementService, PlatformService platformService, String serverName) {
    DefaultMegatronApi api = new DefaultMegatronApi(managementService, platformService, serverName);
    plugins.forEach(plugin -> plugin.init(configuration, api));
  }
}
