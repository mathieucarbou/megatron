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

import com.mycila.megatron.DefaultMegatronConfiguration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class DefaultMegatronServiceConfiguration implements MegatronServiceConfiguration {

  private final DefaultMegatronConfiguration configuration = new DefaultMegatronConfiguration();

  @Override
  public long getStatisticCollectorInterval() {return configuration.getStatisticCollectorInterval();}

  @Override
  public String getProperty(String key, String def) {return configuration.getProperty(key, def);}

  @Override
  public Properties getProperties() {
    return configuration.getProperties();
  }

  public DefaultMegatronServiceConfiguration setStatisticCollectorInterval(long statisticCollectorInterval, TimeUnit timeUnit) {
    configuration.setStatisticCollectorInterval(statisticCollectorInterval, timeUnit);
    return this;
  }

  public DefaultMegatronServiceConfiguration setProperty(String name, String val) {
    configuration.setProperty(name, val);
    return this;
  }

  public DefaultMegatronServiceConfiguration setProperties(Properties properties) {
    configuration.setProperties(properties);
    return this;
  }

  public DefaultMegatronServiceConfiguration loadProperties(File propertyFile) {
    configuration.loadProperties(propertyFile);
    return this;
  }

  public DefaultMegatronServiceConfiguration loadProperties(URL url) {
    configuration.loadProperties(url);
    return this;
  }

  @Override
  public String toString() {
    return configuration.toString();
  }
}
