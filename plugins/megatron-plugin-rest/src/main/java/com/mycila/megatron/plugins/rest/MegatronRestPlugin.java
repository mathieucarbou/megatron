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
package com.mycila.megatron.plugins.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mycila.megatron.AbstractMegatronPlugin;
import com.mycila.megatron.Config;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.Namespace;
import com.mycila.megatron.format.Statistics;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.terracotta.management.model.cluster.Client;
import org.terracotta.management.model.cluster.Node;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.undertow.Handlers.pathTemplate;
import static io.undertow.Handlers.urlDecodingHandler;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * @author Mathieu Carbou
 */
@Namespace("megatron.rest")
public class MegatronRestPlugin extends AbstractMegatronPlugin {

  private final Map<Context, Map<String, Number>> statsPerContexts = new ConcurrentHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();

  @Config private int port = 9470;
  @Config private String bindAddress = "0.0.0.0";

  private Undertow server;

  @Override
  public void enable(MegatronConfiguration configuration) {
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    server = Undertow.builder()
        .addHttpListener(port, bindAddress)
        .setHandler(urlDecodingHandler("UTF-8", pathTemplate(true)

            // platform service endpoints

            .add("/api/v1/platform/server", json((exchange, params) -> Collections.singletonMap("serverName", getApi().getNodeName())))

            .add("/api/v1/platform/dump", json((exchange, params) -> {
              getApi().dumpState();
              return Boolean.TRUE;
            }))

            .add("/api/v1/platform/config", xml((exchange, params) -> getApi().getConfiguration()))

            // topology endpoints

            .add("/api/v1/topology", json((exchange, params) -> getApi().readLiveTopology().toMap()))

            .add("/api/v1/topology/servers", json((exchange, params) -> getApi().readLiveTopology()
                .serverStream()
                .sorted(comparing(Node::getId))
                .map(Server::toMap)
                .peek(map -> map.remove("serverEntities"))
                .collect(toList())))

            .add("/api/v1/topology/clients", json((exchange, params) -> getApi().readLiveTopology()
                .clientStream()
                .sorted(comparing(Node::getId))
                .map(Client::toMap)
                .peek(map -> map.keySet().removeAll(asList("managementRegistry", "connections")))
                .collect(toList())))

            .add("/api/v1/topology/clients/ehcache", json((exchange, params) -> getApi().readLiveTopology()
                .clientStream()
                .filter(client -> client.getName().startsWith("Ehcache:"))
                .sorted(comparing(Node::getId))
                .map(Client::toMap)
                .peek(map -> map.keySet().removeAll(asList("managementRegistry", "connections")))
                .collect(toList())))

            // statistic endpoints

            .add("/api/v1/statistics", json((exchange, params) -> {
              String statNames = params.getOrDefault("statNames", "");
              return statsPerContexts.entrySet()
                  .stream()
                  .map(e -> {
                    Map<String, Object> obj = new LinkedHashMap<>(e.getKey());
                    obj.put("statistics", filter(e.getValue(), statNames));
                    return obj;
                  })
                  .collect(toList());
            }))
            .add("/api/v1/statistics/clients/{clientId}/cacheManagers/{cacheManagerName}/caches/{cacheName}", json((exchange, params) -> {
              String clientId = params.get("clientId");
              String cacheManagerName = params.get("cacheManagerName");
              String cacheName = params.get("cacheName");
              String statNames = params.getOrDefault("statNames", "");
              return filter(findStatistics(context ->
                  clientId.equals(context.get(Client.KEY))
                      && cacheManagerName.equals(context.get("cacheManagerName"))
                      && cacheName.equals(context.get("cacheName"))), statNames);

            }))
            .add("/api/v1/statistics/clients/{clientId}/cacheManagers/{cacheManagerName}", json((exchange, params) -> {
              String clientId = params.get("clientId");
              String cacheManagerName = params.get("cacheManagerName");
              String statNames = params.getOrDefault("statNames", "");
              Map<Context, Map<String, Number>> allStatistics = findAllStatistics(context ->
                  clientId.equals(context.get(Client.KEY))
                      && cacheManagerName.equals(context.get("cacheManagerName"))
                      && context.contains("cacheName"));
              return allStatistics.entrySet()
                  .stream()
                  .map(e -> new AbstractMap.SimpleEntry<>(e.getKey().get("cacheName"), filter(e.getValue(), statNames)))
                  .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            }))
            .add("/api/v1/statistics/clients/{clientId}", json((exchange, params) -> {
              String clientId = params.get("clientId");
              String statNames = params.getOrDefault("statNames", "");
              Map<Context, Map<String, Number>> allStatistics = findAllStatistics(context ->
                  clientId.equals(context.get(Client.KEY))
                      && context.contains("cacheManagerName")
                      && context.contains("cacheName"));
              return allStatistics.entrySet()
                  .stream()
                  .map(e -> new AbstractMap.SimpleEntry<>(e.getKey().get("cacheManagerName"), e))
                  .collect(
                      groupingBy(Map.Entry::getKey,
                          toMap(o -> o.getValue().getKey().get("cacheName"), o -> filter(o.getValue().getValue(), statNames))));
            }))
        ))
        .build();
    logger.info("Listening on port " + port + "...");
    server.start();
  }

  @Override
  public void close() {
    if (enable) {
      statsPerContexts.clear();
      server.stop();
      enable = false;
    }
  }

  @Override
  public void onNotifications(List<ContextualNotification> notifications) {
    if (enable) {
      for (ContextualNotification notification : notifications) {
        switch (notification.getType()) {
          // cleanup the stats "buffer"
          case "SERVER_ENTITY_DESTROYED":
          case "CLIENT_DISCONNECTED":
          case "SERVER_LEFT":
          case "CACHE_REMOVED": {
            statsPerContexts.entrySet().removeIf(e -> e.getKey().contains(notification.getContext()));
            break;
          }
        }
      }
    }
  }

  @Override
  public void onStatistics(List<ContextualStatistics> contextualStatistics) {
    if (enable) {
      for (ContextualStatistics contextualStatistic : contextualStatistics) {
        Map<String, Number> statistics = Statistics.extractStatistics(contextualStatistic);
        logger.trace("onStatistics({})", statistics.size());
        Map<String, Number> stats = statsPerContexts.computeIfAbsent(contextualStatistic.getContext(), context -> new ConcurrentHashMap<>());
        stats.putAll(statistics);
      }
    }
  }

  private HttpHandler json(ParamHandler handler) {
    return exchange -> {
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
      Map<String, String> params = new HashMap<>();
      for (String key : exchange.getQueryParameters().keySet()) {
        params.put(key, exchange.getQueryParameters().get(key).getFirst());
      }
      try {
        Object o = handler.handleRequest(exchange, params);
        exchange.getResponseSender().send(mapper.writeValueAsString(o));
      } catch (RuntimeException e) {
        exchange.getResponseSender().send(mapper.writeValueAsString(Collections.singletonMap("error", e.getLocalizedMessage())));
      }
    };
  }

  private HttpHandler xml(ParamHandler handler) {
    return exchange -> {
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/xml; charset=UTF-8");
      Map<String, String> params = new HashMap<>();
      for (String key : exchange.getQueryParameters().keySet()) {
        params.put(key, exchange.getQueryParameters().get(key).getFirst());
      }
      try {
        Object o = handler.handleRequest(exchange, params);
        exchange.getResponseSender().send(String.valueOf(o));
      } catch (Exception e) {
        exchange.getResponseSender().send(mapper.writeValueAsString(Collections.singletonMap("error", e.getLocalizedMessage())));
      }
    };
  }

  private Map<String, Number> findStatistics(Predicate<Context> p) {
    return statsPerContexts.entrySet()
        .stream()
        .filter(e -> p.test(e.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(Collections.emptyMap());
  }

  private Map<Context, Map<String, Number>> findAllStatistics(Predicate<Context> p) {
    return statsPerContexts.entrySet()
        .stream()
        .filter(e -> p.test(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, Number> filter(Map<String, Number> statistics, String statNames) {
    if (statNames != null) {
      Set<String> statSet = new HashSet<String>(asList(statNames.split(",")));
      return statistics.entrySet()
          .stream()
          .filter(e -> statSet.contains(e.getKey()))
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    return statistics;
  }

  private interface ParamHandler {
    Object handleRequest(HttpServerExchange exchange, Map<String, String> params) throws Exception;
  }

}
