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

import com.tc.classloader.CommonComponent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class DefaultMegatronConfiguration implements MegatronConfiguration {

  private long statisticCollectorInterval = 10_000L;
  private final Properties properties = new Properties();

  @Override
  public long getStatisticCollectorInterval() {
    return statisticCollectorInterval;
  }

  @Override
  public String getProperty(String key, String def) {
    String val = System.getenv(key.toUpperCase().replace('.', '_'));
    if (val != null) {
      return val;
    }
    val = System.getProperty(key);
    if (val != null) {
      return val;
    }
    return properties.getProperty(key, def);
  }

  @Override
  public Properties getProperties() {
    return properties;
  }

  public DefaultMegatronConfiguration loadProperties(File propertyFile) {
    try {
      return loadProperties(propertyFile.toURI().toURL());
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(propertyFile.toString(), e);
    }
  }

  public DefaultMegatronConfiguration loadProperties(URL url) {
    Properties properties = new Properties();
    try (InputStream is = url.openStream()) {
      properties.load(is);
      return setProperties(properties);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to real location " + url + " : " + e.getMessage(), e);
    }
  }

  public DefaultMegatronConfiguration setStatisticCollectorInterval(long statisticCollectorInterval, TimeUnit timeUnit) {
    this.statisticCollectorInterval = TimeUnit.MILLISECONDS.convert(statisticCollectorInterval, timeUnit);
    return this;
  }

  public DefaultMegatronConfiguration setProperty(String name, String val) {
    properties.setProperty(name, val);
    return this;
  }

  @SuppressWarnings("CollectionAddedToSelf")
  public DefaultMegatronConfiguration setProperties(Properties properties) {
    this.properties.putAll(properties);
    return this;
  }

  public DefaultMegatronConfiguration merge(DefaultMegatronConfiguration configuration) {
    this.statisticCollectorInterval = configuration.statisticCollectorInterval;
    this.properties.putAll(configuration.properties);
    return this;
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
