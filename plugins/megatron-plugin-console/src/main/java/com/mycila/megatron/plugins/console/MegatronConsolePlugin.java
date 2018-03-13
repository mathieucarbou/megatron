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
package com.mycila.megatron.plugins.console;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mycila.megatron.AbstractMegatronPlugin;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.Namespace;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.stream.Collectors;

/**
 * @author Mathieu Carbou
 */
@Namespace("megatron.console")
public class MegatronConsolePlugin extends AbstractMegatronPlugin {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public void enable(MegatronConfiguration configuration) {
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  @Override
  public void onNotification(ContextualNotification notification) {
    if (enable) {
      System.out.println("NOTIFICATION: " + notification.getType() + "\n" + json(notification.getContext()));
    }
  }

  @Override
  public void onStatistics(ContextualStatistics statistics) {
    if (enable) {
      System.out.println("STATISTICS:\n - " +
          statistics.getStatistics()
              .entrySet()
              .stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .sorted()
              .collect(Collectors.joining("\n - ")) + "\n" +
          json(statistics.getContext()));
    }
  }

  private String json(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
