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
package com.mycila.megatron.plugins.prometheus.statsd;

import com.mycila.megatron.AbstractMegatronUdpPlugin;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.Namespace;
import com.mycila.megatron.format.DefaultFormatter;
import com.mycila.megatron.format.Formatter;
import com.mycila.megatron.format.Statistics;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.Map;

/**
 * @author Mathieu Carbou
 */

@Namespace("megatron.prometheus.statsd")
public class MegatronPrometheusStatsDPlugin extends AbstractMegatronUdpPlugin {

  private Formatter formatter;

  @Override
  public void enable(MegatronConfiguration configuration) {
    super.enable(configuration);
    formatter = new DefaultFormatter()
        .prefixSeparator(".")
        .globalPrefix(prefix)
        .tagSupport()
        .globalTags(tags)
        .tagAssignementChar(":")
        .tagSeparatorChar(",")
    ;
    formatter.init();
  }

  @Override
  public void onNotification(ContextualNotification notification) {
    if (enable) {
      logger.trace("onNotification({})", notification.getType());
      String metric = formatter.formatMetricName("events", notification, notification.getType());
      String value = formatter.formatValue(1);
      String tags = formatter.formatTags(notification);
      send(metric, value, tags, "c");
    }
  }

  @Override
  public void onStatistics(ContextualStatistics contextualStatistics) {
    if (enable) {
      Map<String, Number> statistics = Statistics.extractStatistics(contextualStatistics);
      logger.trace("onStatistics({})", statistics.size());
      String tags = formatter.formatTags(contextualStatistics);
      for (Map.Entry<String, Number> entry : statistics.entrySet()) {
        String metric = formatter.formatMetricName("statistics", contextualStatistics, entry.getKey());
        String value = formatter.formatValue(entry.getValue());
        send(metric, value, tags, "g");
      }
    }
  }

  private void send(String metric, String value, String tags, String type) {
    String message = metric + ":" + value + "|" + type;
    if (!tags.isEmpty()) {
      message = message + "|#" + tags;
    }
    client.send(message);
  }

}
