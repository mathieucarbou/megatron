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
package com.mycila.megatron.format;

import com.tc.classloader.CommonComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.statistics.Sample;
import org.terracotta.statistics.registry.Statistic;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class Statistics {

  private static final Logger LOGGER = LoggerFactory.getLogger(Statistics.class);
  private static final Comparator<Number> NUMBER_COMPARATOR = Comparator.comparingDouble(Number::doubleValue);

  public static Map<String, Number> extractStatistics(ContextualStatistics contextualStatistics) {
    Map<String, Statistic<? extends Serializable>> statistics = contextualStatistics.getStatistics();
    Map<String, Number> reduced = new TreeMap<>();
    for (Map.Entry<String, Statistic<? extends Serializable>> entry : statistics.entrySet()) {
      String name = entry.getKey();
      Statistic<? extends Serializable> statistic = entry.getValue();
      switch (statistic.getType()) {

        case RATE:
        case RATIO:
          // aggregate by averaging the samples
          statistic.getSamples()
              .stream()
              .map(Sample::getSample)
              .filter(Number.class::isInstance)
              .map(Number.class::cast)
              .mapToDouble(Number::doubleValue)
              .average()
              .ifPresent(avg -> reduced.put(name, avg));
          break;

        case COUNTER:
        case GAUGE:
          // aggregate by taking the maximum from the samples
          statistic.getSamples()
              .stream()
              .map(Sample::getSample)
              .filter(Number.class::isInstance)
              .map(Number.class::cast)
              .max(NUMBER_COMPARATOR)
              .ifPresent(max -> reduced.put(name, max));
          break;

        case TABLE:
          // we cannot support tables
          break;

        default:
          LOGGER.trace("Unsupported statistic: {}", statistic);
      }
    }
    return reduced;
  }

}
