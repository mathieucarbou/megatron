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
import org.terracotta.management.model.cluster.Client;
import org.terracotta.management.model.cluster.ClientIdentifier;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.cluster.ServerEntity;
import org.terracotta.management.model.cluster.Stripe;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.context.Contextual;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class DefaultFormatter implements Formatter {

  private static final String MEGATRON_ENTITY = "com.mycila.megatron.server.entity.MegatronEntity";
  private static final String EHCACHE_ENTITY = "org.ehcache.";
  private static final String DATASET_ENTITY = "com.terracottatech.store.";

  private static final ThreadLocal<NumberFormat> NUMBER_FORMATTERS = ThreadLocal.withInitial(() -> {
    final NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
    numberFormatter.setGroupingUsed(false);
    numberFormatter.setMaximumFractionDigits(6);
    if (numberFormatter instanceof DecimalFormat) { // better safe than a runtime error
      final DecimalFormat decimalFormat = (DecimalFormat) numberFormatter;
      final DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
      symbols.setNaN("NaN");
      decimalFormat.setDecimalFormatSymbols(symbols);
    }
    return numberFormatter;
  });

  private String globalPrefix = "";
  private String prefixSeparator = ".";

  private boolean tagSupport = false;
  private String[] globalTags = new String[0];
  private String globalTagLine = "";
  private String tagAssignement = "=";
  private String tagSeparator = ",";

  public DefaultFormatter globalPrefix(String globalPrefix) {
    this.globalPrefix = globalPrefix;
    return this;
  }

  public DefaultFormatter prefixSeparator(String prefixSeparator) {
    this.prefixSeparator = prefixSeparator;
    return this;
  }

  public DefaultFormatter tagSupport() {
    this.tagSupport = true;
    return this;
  }

  public DefaultFormatter globalTags(String... globalTags) {
    this.globalTags = globalTags;
    return this;
  }

  public DefaultFormatter tagAssignementChar(String tagAssignement) {
    this.tagAssignement = tagAssignement;
    return this;
  }

  public DefaultFormatter tagSeparatorChar(String tagSeparator) {
    this.tagSeparator = tagSeparator;
    return this;
  }

  @Override
  public void init() {
    globalTagLine = globalTags.length == 0 ? "" : Stream.of(globalTags)
        .map(DefaultFormatter::escape)
        .collect(Collectors.joining(tagSeparator));
  }

  @Override
  public String formatMetricName(String prefix, Contextual contextual, String metricName) {
    metricName = escape(metricName);
    String metric = tagSupport ? buildSimplePrefix(contextual) : buildFullPrefix(contextual);
    // add suffix (exact stat name)
    metric = metric.isEmpty() ? metricName : metricName.isEmpty() ? metric : (metric + prefixSeparator + metricName);
    // add prefix (i.e. event, statistic, etc
    metric = metric.isEmpty() ? prefix : prefix.isEmpty() ? metric : (prefix + prefixSeparator + metric);
    // add global prefix (i.e. megatron)
    metric = metric.isEmpty() ? globalPrefix : globalPrefix.isEmpty() ? metric : (globalPrefix + prefixSeparator + metric);
    return metric;
  }

  @Override
  public String formatTags(Contextual contextual) {
    if (!tagSupport) {
      return "";
    }
    String tags = tags(contextual.getContext()).entrySet()
        .stream()
        .map(e -> e.getKey() + tagAssignement + e.getValue())
        .collect(Collectors.joining(tagSeparator));
    tags = tags.isEmpty() ? globalTagLine : (globalTagLine + tagSeparator + tags);
    return tags;
  }

  @Override
  public String formatValue(Serializable value) {
    if (value instanceof Number) {
      Number v = (Number) value;
      if (value instanceof Double || value instanceof Float) {
        return NUMBER_FORMATTERS.get().format(v.doubleValue());
      } else {
        return value.toString();
      }
    }
    return String.valueOf(value);
  }

  private static Map<String, String> tags(Context context) {
    Map<String, String> tags = new TreeMap<>(context);
    tags.keySet().removeAll(Arrays.asList("consumerId", "collectorId", ServerEntity.KEY, Server.KEY, Stripe.KEY, Client.KEY));
    if (context.contains(Client.KEY)) {
      tags.put("hostAddress", ClientIdentifier.valueOf(context.get(Client.KEY)).getHostAddress());
      tags.put("pid", String.valueOf(ClientIdentifier.valueOf(context.get(Client.KEY)).getPid()));
    }
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      entry.setValue(escape(entry.getValue()));
    }
    return tags;
  }

  private String buildSimplePrefix(Contextual contextual) {
    Context context = contextual.getContext();
    List<String> prefixes = new ArrayList<>();
    if (context.contains(Client.KEY)) {
      prefixes.add("client");
      ClientIdentifier clientIdentifier = ClientIdentifier.valueOf(context.get(Client.KEY));
      if (clientIdentifier.getName().startsWith("Ehcache:")) {
        prefixes.add("ehcache");
      } else if (clientIdentifier.getName().startsWith("Store:")) {
        prefixes.add("dataset");
      } else {
        prefixes.add("unknown");
      }
    } else if (context.contains(Server.KEY)) {
      prefixes.add("server");
      String entityType = context.get(ServerEntity.TYPE_KEY);
      if (entityType != null) {
        if (MEGATRON_ENTITY.equals(entityType)) {
          prefixes.add("platform");
        } else {
          prefixes.add("entity");
          if (entityType.startsWith(EHCACHE_ENTITY)) {
            prefixes.add("ehcache");
          } else if (entityType.startsWith(DATASET_ENTITY)) {
            prefixes.add("dataset");
          } else {
            prefixes.add("unknown");
          }
        }
      }
    }
    return String.join(prefixSeparator, prefixes);
  }

  private String buildFullPrefix(Contextual contextual) {
    Context context = contextual.getContext();
    List<String> prefixes = new ArrayList<>();
    if (context.contains(Client.KEY)) {
      ClientIdentifier clientIdentifier = ClientIdentifier.valueOf(context.get(Client.KEY));
      add(prefixes, "clients", clientIdentifier.getHostAddress());
      add(prefixes, "procs", String.valueOf(clientIdentifier.getPid()));
      if (clientIdentifier.getName().startsWith("Ehcache:")) {
        add(prefixes, "cacheManagers", context.get("cacheManagerName"));
        add(prefixes, "caches", context.get("cacheName"));
      } else if (clientIdentifier.getName().startsWith("Store:")) {
        add(prefixes, "datasets", context.get("datasetName"));
        add(prefixes, "instances", context.get("datasetInstanceName"));
      }
    } else if (context.contains(Server.KEY)) {
      add(prefixes, "servers", context.get(Server.KEY));
      String entityType = context.get(ServerEntity.TYPE_KEY);
      if (entityType != null) {
        if (MEGATRON_ENTITY.equals(entityType)) {
          // example: add(prefixes, 'OffHeapResource', 'offheap-1')
          add(prefixes, context.get("type"), context.get("alias"));
        } else {
          if (entityType.startsWith(EHCACHE_ENTITY)) {
            add(prefixes, "entities" + prefixSeparator + "ehcache", context.get(ServerEntity.NAME_KEY));
          } else if (entityType.startsWith(DATASET_ENTITY)) {
            add(prefixes, "entities" + prefixSeparator + "dataset", context.get(ServerEntity.NAME_KEY));
          } else {
            add(prefixes, "entities" + prefixSeparator + "unknown", context.get(ServerEntity.NAME_KEY));
          }
          add(prefixes, context.get("type"), context.get("alias"));
        }
      }
    }
    return String.join(prefixSeparator, prefixes);
  }

  private static void add(List<String> prefixes, String key, String value) {
    if (value != null) {
      prefixes.add(key);
      prefixes.add(escape(value));
    }
  }

  private static String escape(String s) {
    return s.replaceAll("[@$,.:|#;]+", "_");
  }

}
