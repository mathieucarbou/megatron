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
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

  public AbstractMegatronPlugin() {
    converters.put(String.class, s -> s);
    converters.put(int.class, Integer::parseInt);
    converters.put(long.class, Long::parseLong);
    converters.put(boolean.class, Boolean::parseBoolean);
    converters.put(float.class, Float::parseFloat);
    converters.put(double.class, Double::parseDouble);
    converters.put(List.class, Double::parseDouble);
  }

  @Override
  public final void init(MegatronConfiguration configuration, MegatronApi api) {
    Namespace namespace = getClass().getAnnotation(Namespace.class);
    String ns = namespace == null ? "" : (namespace.value() + ".");

    Class<?> c = getClass();
    while (c != Object.class) {
      Stream.of(c.getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Config.class))
          .forEach(field -> {
            Config config = field.getAnnotation(Config.class);
            String name = config.value().isEmpty() ? field.getName() : config.value();
            String value = configuration.getProperty(ns + name);
            field.setAccessible(true);
            if (value != null) {
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
                field.set(this, converted);
              } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            }
            try {
              this.pluginConfig.put(ns + name, field.get(this));
            } catch (IllegalAccessException e) {
              throw new RuntimeException(e);
            }
          });
      c = c.getSuperclass();
    }

    if (logger.isInfoEnabled()) {
      StringBuilder log = new StringBuilder("init()");
      for (Map.Entry<String, Object> entry : pluginConfig.entrySet()) {
        Object value = entry.getValue();
        if (value.getClass().isArray()) {
          if (value.getClass().getComponentType().isPrimitive()) {
            try {
              value = Arrays.class.getMethod("toString", value.getClass()).invoke(null, value);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
              throw new RuntimeException(e);
            }
          } else {
            value = Arrays.toString((Object[]) value);
          }
        }
        log.append("\n - ").append(entry.getKey()).append("=").append(value);
      }
      logger.info("{}", log);
    }

    if (enable) {
      enable(configuration, api);
    }
  }

  @Override
  public final boolean isEnable() {
    return enable;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + pluginConfig;
  }

  protected void enable(MegatronConfiguration configuration, MegatronApi api) {}

}
