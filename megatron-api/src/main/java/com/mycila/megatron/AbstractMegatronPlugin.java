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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public abstract class AbstractMegatronPlugin implements MegatronPlugin {

  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected final Map<Class<?>, Function<String, Object>> converters = new IdentityHashMap<>();

  @Config protected boolean enable = false;

  private final Map<String, Object> pluginConfig = new TreeMap<>();

  private volatile boolean initialized;
  private volatile MegatronApi api;

  public AbstractMegatronPlugin() {
    converters.put(String.class, s -> s);
    converters.put(int.class, Integer::parseInt);
    converters.put(long.class, Long::parseLong);
    converters.put(boolean.class, Boolean::parseBoolean);
    converters.put(float.class, Float::parseFloat);
    converters.put(double.class, Double::parseDouble);
    converters.put(List.class, Double::parseDouble);
    converters.put(URL.class, s -> {
      try {
        return new URL(s);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(s, e);
      }
    });
  }

  @Override
  public final void setApi(MegatronApi api) {
    this.api = api;
  }

  @Override
  public final void init(MegatronConfiguration configuration) throws ConfigurationException {
    if (!initialized) {
      try {
        logger.trace("init()");

        injectConfig(configuration);

        if (logger.isInfoEnabled()) {
          StringBuilder log = new StringBuilder("Plugin initialized:");
          for (Map.Entry<String, Object> entry : pluginConfig.entrySet()) {
            Object value = entry.getValue();
            if (value != null && value.getClass().isArray()) {
              if (value.getClass().getComponentType().isPrimitive()) {
                try {
                  value = Arrays.class.getMethod("toString", value.getClass()).invoke(null, value);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                  throw new ConfigurationException(e);
                }
              } else {
                value = Arrays.toString((Object[]) value);
              }
            }
            log.append("\n - ").append(entry.getKey()).append("=").append(value);
          }
          logger.info("{}", log);
        }
      } catch (ConfigurationException e) {
        enable = false;
        throw e;
      }

      initialized = true;

      if (enable) {
        logger.info("Enabling plugin...");
        enable(configuration);
      }
    }
  }

  private void injectConfig(MegatronConfiguration configuration) throws ConfigurationException {
    Namespace namespace = getClass().getAnnotation(Namespace.class);
    String ns = namespace == null ? "" : (namespace.value() + ".");

    Class<?> c = getClass();
    while (c != Object.class) {
      Stream.of(c.getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Config.class))
          .forEach(field -> {
            Config config = field.getAnnotation(Config.class);
            String name = config.value().isEmpty() ? field.getName() : config.value();
            String propertyKey = ns + name;
            String value = configuration.getProperty(propertyKey);
            if (value == null) {
              if (config.required()) {
                throw new ConfigurationException("Missing configuration '" + propertyKey + "' for plugin " + getClass().getSimpleName());
              }
            } else {
              Object converted;
              if (field.getType().isArray()) {
                String[] values = value.split(config.split());
                Class<?> arrayType = field.getType().getComponentType();
                converted = Array.newInstance(arrayType, values.length);
                for (int i = 0; i < values.length; i++) {
                  Array.set(converted, i, converters.get(arrayType).apply(values[i]));
                }
              } else {
                converted = converters.get(field.getType()).apply(value);
              }
              try {
                if (!field.isAccessible()) {
                  field.setAccessible(true);
                }
                field.set(this, converted);
              } catch (IllegalAccessException e) {
                throw new ConfigurationException(e);
              }
            }
            try {
              if (!field.isAccessible()) {
                field.setAccessible(true);
              }
              this.pluginConfig.put(propertyKey, field.get(this));
            } catch (IllegalAccessException e) {
              throw new ConfigurationException(e);
            }
          });
      c = c.getSuperclass();
    }
  }

  @Override
  public final boolean isEnable() {
    return enable;
  }

  @Override
  public final boolean isInitialized() {
    return initialized;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + pluginConfig;
  }

  protected void enable(MegatronConfiguration configuration) {}

  protected MegatronApi getApi() {
    return Objects.requireNonNull(api);
  }

}
