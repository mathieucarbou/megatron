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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
public class RunServers {

  private static final Random RANDOM = new Random();
  private static final int N_SERVERS = 2;
  private static final String RESOURCE_CONFIG =
      "<config xmlns:mc='http://www.mycila.com/config/megatron-config'>" +
          "  <mc:megatron-config>" +
          "    <mc:statisticCollectorInterval unit=\"seconds\">10</mc:statisticCollectorInterval>" +

          "    <mc:set name=\"megatron.console.enable\" value=\"false\"/>" +

          "    <mc:set name=\"megatron.rest.enable\" value=\"false\"/>" +
          "    <mc:set name=\"megatron.rest.bindAddress\" value=\"0.0.0.0\"/>" +
          "    <mc:set name=\"megatron.rest.port\" value=\"9470\"/>" +

          "    <mc:set name=\"megatron.datadog.enable\" value=\"false\"/>\n" +
          "    <mc:set name=\"megatron.datadog.server\" value=\"localhost\"/>\n" +
          "    <mc:set name=\"megatron.datadog.port\" value=\"8125\"/>\n" +
          "    <mc:set name=\"megatron.datadog.prefix\" value=\"megatron\"/>\n" +
          "    <mc:set name=\"megatron.datadog.tags\" value=\"stripe:stripe1,cluster:MyCluster\"/>\n" +
          "    <mc:set name=\"megatron.datadog.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.datadog.queueSize\" value=\"-1\"/>\n" +

          "    <mc:set name=\"megatron.librato.enable\" value=\"false\"/>\n" +
          "    <mc:set name=\"megatron.librato.server\" value=\"localhost\"/>\n" +
          "    <mc:set name=\"megatron.librato.port\" value=\"8135\"/>\n" +
          "    <mc:set name=\"megatron.librato.prefix\" value=\"megatron\"/>\n" +
          "    <mc:set name=\"megatron.librato.tags\" value=\"stripe=stripe1,cluster=MyCluster\"/>\n" +
          "    <mc:set name=\"megatron.librato.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.librato.queueSize\" value=\"-1\"/>\n" +

          "    <mc:set name=\"megatron.graphite.enable\" value=\"false\"/>\n" +
          "    <mc:set name=\"megatron.graphite.server\" value=\"localhost\"/>\n" +
          "    <mc:set name=\"megatron.graphite.port\" value=\"2003\"/>\n" +
          "    <mc:set name=\"megatron.graphite.prefix\" value=\"megatron\"/>\n" +
          "    <mc:set name=\"megatron.graphite.tags\" value=\"stripe=stripe1;cluster=MyCluster\"/>\n" +
          "    <mc:set name=\"megatron.graphite.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.graphite.queueSize\" value=\"-1\"/>\n" +

          "    <mc:set name=\"megatron.statsd.enable\" value=\"false\"/>\n" +
          "    <mc:set name=\"megatron.statsd.server\" value=\"statsd.hostedgraphite.com\"/>\n" +
          "    <mc:set name=\"megatron.statsd.port\" value=\"8125\"/>" +
          "    <mc:set name=\"megatron.statsd.prefix\" value=\"cb54b2cb-cb68-49f0-8250-0bb79f3fee19.megatron\"/>" +
          "    <mc:set name=\"megatron.statsd.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.statsd.queueSize\" value=\"-1\"/>\n" +

          "    <mc:set name=\"megatron.prometheus.statsd.enable\" value=\"false\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.server\" value=\"localhost\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.port\" value=\"9125\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.prefix\" value=\"megatron\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.tags\" value=\"stripe:stripe1,cluster:MyCluster\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.statsd.queueSize\" value=\"-1\"/>\n" +

          "    <mc:set name=\"megatron.prometheus.gateway.enable\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.gateway.url\" value=\"http://localhost:9091/metrics/job/megatron\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.gateway.prefix\" value=\"megatron\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.gateway.tags\" value=\"stripe=&quot;stripe1&quot;,cluster=&quot;MyCluster&quot;\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.gateway.async\" value=\"true\"/>\n" +
          "    <mc:set name=\"megatron.prometheus.gateway.queueSize\" value=\"-1\"/>\n" +

          "  </mc:megatron-config>" +
          "</config>" +
          "<config xmlns:ohr='http://www.terracotta.org/config/offheap-resource'>" +
          "  <ohr:offheap-resources>" +
          "    <ohr:resource name=\"offheap-1\" unit=\"MB\">128</ohr:resource>" +
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
            .defaultServerResource("offheap-1")
            .resourcePool("pool-a", 60, MemoryUnit.MB, "offheap-1")
            .resourcePool("pool-b", 60, MemoryUnit.MB))
        // management config
        .using(new DefaultManagementRegistryConfiguration()
            .addTags("webapp-1", "server-node-1")
            .setCacheManagerAlias("cm1"))
        // cache config
        .withCache("cache-1", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB)
                .with(clusteredDedicated("offheap-1", 8, MemoryUnit.MB)))
            .build())
        .withCache("cache-2", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB)
                .with(clusteredShared("pool-a")))
            .build())
        .withCache("cache-3", newCacheConfigurationBuilder(
            String.class, byte[].class,
            newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(5, MemoryUnit.MB)
                .with(clusteredShared("pool-b")))
            .build())
        .build(true);

    List<Cache<String, byte[]>> caches = Arrays.asList(
        cacheManager.getCache("cache-1", String.class, byte[].class),
        cacheManager.getCache("cache-2", String.class, byte[].class),
        cacheManager.getCache("cache-3", String.class, byte[].class));

    pounder = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          byte[] data = new byte[RANDOM.nextInt(1024)];
          caches.get(RANDOM.nextInt(caches.size())).put("key-" + RANDOM.nextInt(1000), data);
          caches.get(RANDOM.nextInt(caches.size())).get("key-" + RANDOM.nextInt(1000));
          caches.get(RANDOM.nextInt(caches.size())).remove("key-" + RANDOM.nextInt(1000));
        } catch (Exception ignored) {
        }
        try {
          Thread.sleep(250);
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

  @Test
  public void test_statistics() throws Exception {
    pounder.start();

    // ensure stats are collected by the rest plugin
    //Thread.sleep(180_000);
    System.in.read();
  }

}
