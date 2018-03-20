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
package com.mycila.megatron.tests;

import com.mashape.unirest.http.Unirest;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.management.registry.DefaultManagementRegistryConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.terracotta.testing.rules.Cluster;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder.clusteredDedicated;
import static org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder.clusteredShared;
import static org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder.cluster;
import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder;
import static org.terracotta.testing.rules.BasicExternalClusterBuilder.newCluster;

/**
 * @author Mathieu Carbou
 */
@RunWith(JUnit4.class)
public class RestIT {

  private static final Random RANDOM = new Random();
  private static final int N_SERVERS = 1;
  private static final String REST = "http://localhost:9470/api/v1";
  private static final String RESOURCE_CONFIG =
      "<config xmlns:mc='http://www.mycila.com/config/megatron-config'>" +
          "  <mc:megatron-config>" +
          "    <mc:statisticCollectorInterval unit=\"seconds\">1</mc:statisticCollectorInterval>" +
          "    <mc:set name=\"megatron.console.enable\" value=\"false\"/>" +
          "    <mc:set name=\"megatron.rest.enable\" value=\"true\"/>" +
          "    <mc:set name=\"megatron.rest.bindAddress\" value=\"0.0.0.0\"/>" +
          "    <mc:set name=\"megatron.rest.port\" value=\"9470\"/>" +
          "  </mc:megatron-config>" +
          "</config>" +
          "<config xmlns:ohr='http://www.terracotta.org/config/offheap-resource'>" +
          "  <ohr:offheap-resources>" +
          "    <ohr:resource name=\"main\" unit=\"MB\">128</ohr:resource>" +
          "  </ohr:offheap-resources>" +
          "</config>" +
          "\n";

  @Rule
  public Cluster voltron = newCluster(N_SERVERS)
      .in(new File("target/galvan"))
      .withServiceFragment(RESOURCE_CONFIG)
      .build();

  private CacheManager cacheManager;
  private Thread pounder;

  @Before
  public void before() throws Exception {
    voltron.getClusterControl().waitForActive();
    voltron.getClusterControl().waitForRunningPassivesInStandby();

    Unirest.setObjectMapper(new UnirestJackson());

    cacheManager = newCacheManagerBuilder()
        // cluster config
        .with(cluster(voltron.getConnectionURI().resolve("/cm1-entity"))
            .autoCreate()
            .defaultServerResource("main")
            .resourcePool("pool-a", 28, MemoryUnit.MB, "main")
            .resourcePool("pool-b", 16, MemoryUnit.MB)) // will take from primary-server-resource
        // management config
        .using(new DefaultManagementRegistryConfiguration()
            .addTags("webapp-1", "server-node-1")
            .setCacheManagerAlias("cm1"))
        // cache config
        .withCache("cache-1", newCacheConfigurationBuilder(
            String.class, String.class,
            newResourcePoolsBuilder()
                .heap(10, EntryUnit.ENTRIES)
                .offheap(1, MemoryUnit.MB)
                .with(clusteredDedicated("main", 4, MemoryUnit.MB)))
            .build())
        .withCache("cache-2", newCacheConfigurationBuilder(
            String.class, String.class,
            newResourcePoolsBuilder()
                .heap(10, EntryUnit.ENTRIES)
                .offheap(1, MemoryUnit.MB)
                .with(clusteredShared("pool-a")))
            .build())
        .withCache("cache-3", newCacheConfigurationBuilder(
            String.class, String.class,
            newResourcePoolsBuilder()
                .heap(10, EntryUnit.ENTRIES)
                .offheap(1, MemoryUnit.MB)
                .with(clusteredShared("pool-b")))
            .build())
        .build(true);

    Cache<String, String> cache = cacheManager.getCache("cache-1", String.class, String.class);

    pounder = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        cache.put("key-" + RANDOM.nextInt(1000), "value-" + RANDOM.nextInt(1000));
        cache.get("key-" + RANDOM.nextInt(1000));
        cache.remove("key-" + RANDOM.nextInt(1000));
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          return;
        }
      }
    });
  }

  @After
  public void after() throws Exception {
    Unirest.shutdown();
    if (cacheManager != null) {
      cacheManager.close();
      pounder.interrupt();
      pounder.join();
    }
    voltron.getClusterControl().terminateActive();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_platform_server() throws Exception {
    Map<String, ?> map = Unirest.get(REST + "/platform/server").asObject(Map.class).getBody();
    System.out.println("==> active server=" + map);
    assertThat(map).hasSize(1);
    assertThat(map).containsKey("serverName");
  }

  @Test
  public void test_platform_dump() throws Exception {
    Boolean b = Unirest.get(REST + "/platform/dump").asObject(Boolean.class).getBody();
    assertThat(b).isTrue();
  }

  @Test
  public void test_platform_config() throws Exception {
    String xml = Unirest.get(REST + "/platform/config").asObject(String.class).getBody();
    System.out.println("==> config\n" + xml);
    assertThat(xml).contains(
        "<plugins>\n" +
            "        <config>\n" +
            "            <mc:megatron-config xmlns:mc=\"http://www.mycila.com/config/megatron-config\">\n" +
            "                <mc:statisticCollectorInterval unit=\"seconds\">1</mc:statisticCollectorInterval>\n" +
            "                <mc:set name=\"megatron.console.enable\" value=\"false\"/>\n" +
            "                <mc:set name=\"megatron.rest.enable\" value=\"true\"/>\n" +
            "                <mc:set name=\"megatron.rest.bindAddress\" value=\"0.0.0.0\"/>\n" +
            "                <mc:set name=\"megatron.rest.port\" value=\"9470\"/>\n" +
            "            </mc:megatron-config>\n" +
            "        </config>\n" +
            "        <config>\n" +
            "            <ohr:offheap-resources xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\">\n" +
            "                <ohr:resource name=\"main\" unit=\"MB\">128</ohr:resource>\n" +
            "            </ohr:offheap-resources>\n" +
            "        </config>\n" +
            "    </plugins>");
  }

  @Test
  public void test_topology() throws Exception {
    Map map = Unirest.get(REST + "/platform/server").asObject(Map.class).getBody();
    String active = (String) map.get("serverName");

    Map topology = Unirest.get(REST + "/topology").asObject(Map.class).getBody();
    System.out.println("==> topology:\n" + topology);

    assertThat(topology.toString()).contains(active);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_topology_servers() throws Exception {
    Map map = Unirest.get(REST + "/platform/server").asObject(Map.class).getBody();
    String active = (String) map.get("serverName");

    List<Map<String, Object>> servers = Unirest.get(REST + "/topology/servers").asObject(List.class).getBody();
    System.out.println("==> servers:\n" + servers);

    assertThat(servers).hasSize(N_SERVERS);
    assertThat(servers.stream()).anyMatch(server -> active.equals(server.get("serverName")));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_topology_clients() throws Exception {
    List<Map<String, Object>> clients = Unirest.get(REST + "/topology/clients").asObject(List.class).getBody();
    System.out.println("==> clients:\n" + clients);
    assertThat(clients).hasSize(1);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_topology_clients_ehcache() throws Exception {
    List<Map<String, Object>> clients = Unirest.get(REST + "/topology/clients").asObject(List.class).getBody();
    System.out.println("==> clients:\n" + clients);
    assertThat(clients).hasSize(1);
    assertThat((String) clients.get(0).get("clientId")).contains(":Ehcache:");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_statistics() throws Exception {
    pounder.start();

    // ensure stats are collected by the rest plugin
    Thread.sleep(4_000);

    List<Map<String, Object>> list = Unirest.get(REST + "/statistics")
        .queryString("statNames", "Cache:PutCount,OffHeapResource:AllocatedMemory,Pool:AllocatedSize,Store:AllocatedMemory")
        .asObject(List.class).getBody();
    System.out.println(list);
    assertThat(list).hasSize(10);
    for (Map<String, Object> context : list) {
      Map<String, Number> stats = (Map<String, Number>) context.get("statistics");
      assertThat(stats).size().isLessThanOrEqualTo(5);
      assertThat(stats).size().isGreaterThan(0);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_statistics_client_cm_cache() throws Exception {
    pounder.start();

    // ensure stats are collected by the rest plugin
    Thread.sleep(4_000);

    List<Map<String, Object>> clients = Unirest.get(REST + "/topology/clients").asObject(List.class).getBody();
    String clientId = (String) clients.get(0).get("clientId");

    Map<String, Object> stats = Unirest.get(REST + "/statistics/clients/{clientId}/cacheManagers/{cacheManagerName}/caches/{cacheName}")
        .routeParam("clientId", clientId)
        .routeParam("cacheManagerName", "cm1")
        .routeParam("cacheName", "cache-1")
        .queryString("statNames", "Cache:PutCount,Cache:HitCount,Cache:INEXISTING")
        .asObject(Map.class).getBody();
    System.out.println(stats);
    assertThat(stats).hasSize(2);
    assertThat(stats).containsKeys("Cache:PutCount", "Cache:HitCount");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_statistics_client_cm() throws Exception {
    pounder.start();

    // ensure stats are collected by the rest plugin
    Thread.sleep(4_000);

    List<Map<String, Object>> clients = Unirest.get(REST + "/topology/clients").asObject(List.class).getBody();
    String clientId = (String) clients.get(0).get("clientId");

    Map<String, Map<String, Object>> caches = Unirest.get(REST + "/statistics/clients/{clientId}/cacheManagers/{cacheManagerName}")
        .routeParam("clientId", clientId)
        .routeParam("cacheManagerName", "cm1")
        .queryString("statNames", "Cache:PutCount,Cache:HitCount,Cache:INEXISTING")
        .asObject(Map.class).getBody();
    System.out.println(caches);
    assertThat(caches).hasSize(3);
    Map<String, Object> stats = caches.get("cache-1");
    assertThat(stats).hasSize(2);
    assertThat(stats).containsKeys("Cache:PutCount", "Cache:HitCount");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_statistics_client() throws Exception {
    pounder.start();

    // ensure stats are collected by the rest plugin
    Thread.sleep(4_000);

    List<Map<String, Object>> clients = Unirest.get(REST + "/topology/clients").asObject(List.class).getBody();
    String clientId = (String) clients.get(0).get("clientId");

    Map<String, Map<String, Map<String, Object>>> cms = Unirest.get(REST + "/statistics/clients/{clientId}")
        .routeParam("clientId", clientId)
        .queryString("statNames", "Cache:PutCount,Cache:HitCount,Cache:INEXISTING")
        .asObject(Map.class).getBody();
    System.out.println(cms);
    assertThat(cms).hasSize(1);
    Map<String, Map<String, Object>> caches = cms.get("cm1");
    assertThat(caches).hasSize(3);
    Map<String, Object> stats = caches.get("cache-1");
    assertThat(stats).hasSize(2);
    assertThat(stats).containsKeys("Cache:PutCount", "Cache:HitCount");
  }

}
