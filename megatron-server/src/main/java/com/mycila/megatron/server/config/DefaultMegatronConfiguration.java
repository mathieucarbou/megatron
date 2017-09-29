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
package com.mycila.megatron.server.config;

import com.mycila.megatron.MegatronConfiguration;
import com.tc.classloader.CommonComponent;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.entity.StateDumpable;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class DefaultMegatronConfiguration implements MegatronConfiguration, StateDumpable {

  private long statisticCollectorInterval = 5_000L;
  private final Properties properties = new Properties();

  @Override
  public long getStatisticCollectorInterval() {
    return statisticCollectorInterval;
  }

  @Override
  public String getProperty(String key, String def) {
    return properties.getProperty(key, def);
  }

  @Override
  public void addStateTo(StateDumpCollector stateDumpCollector) {
    stateDumpCollector.addState("statisticCollectorInterval", String.valueOf(stateDumpCollector));
    stateDumpCollector.addState("properties", properties);
  }

  public void setStatisticCollectorInterval(long statisticCollectorInterval, TimeUnit timeUnit) {
    this.statisticCollectorInterval = TimeUnit.MILLISECONDS.convert(statisticCollectorInterval, timeUnit);
  }

  public void setProperty(String name, String val) {
    properties.setProperty(name, val);
  }

  public void merge(DefaultMegatronConfiguration configuration) {
    this.statisticCollectorInterval = configuration.statisticCollectorInterval;
    this.properties.putAll(configuration.properties);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DefaultMegatronConfiguration{");
    sb.append("statisticCollectorInterval=").append(statisticCollectorInterval);
    sb.append(", properties=").append(properties);
    sb.append('}');
    return sb.toString();
  }
}
