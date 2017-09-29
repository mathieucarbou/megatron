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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.terracotta.connection.Connection;
import org.terracotta.connection.ConnectionFactory;
import org.terracotta.connection.ConnectionPropertyNames;
import org.terracotta.management.entity.nms.NmsConfig;
import org.terracotta.management.entity.nms.client.DefaultNmsService;
import org.terracotta.management.entity.nms.client.NmsEntity;
import org.terracotta.management.entity.nms.client.NmsEntityFactory;
import org.terracotta.management.model.message.Message;
import org.terracotta.testing.rules.Cluster;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.terracotta.testing.rules.BasicExternalClusterBuilder.newCluster;

/**
 * @author Mathieu Carbou
 */
@RunWith(JUnit4.class)
public class PackagingIT {

  private static final String RESOURCE_CONFIG =
      "<config xmlns:ohr='http://www.terracotta.org/config/offheap-resource'>" +
          "  <ohr:offheap-resources>" +
          "    <ohr:resource name=\"offheap-1\" unit=\"MB\">64</ohr:resource>" +
          "  </ohr:offheap-resources>" +
          "</config>" +
          "\n";

  private static final int N_SERVERS = 1;

  @Rule
  public Cluster voltron = newCluster(N_SERVERS)
      .in(new File("target/galvan"))
      .withServiceFragment(RESOURCE_CONFIG)
      .build();

  @Before
  public void before() throws Exception {
    voltron.getClusterControl().waitForActive();
    voltron.getClusterControl().waitForRunningPassivesInStandby();
  }

  @After
  public void after() throws Exception {
    voltron.getClusterControl().terminateActive();
  }

  @Test
  public void test() throws Exception {
    Properties properties = new Properties();
    properties.setProperty(ConnectionPropertyNames.CONNECTION_NAME, getClass().getSimpleName());
    properties.setProperty(ConnectionPropertyNames.CONNECTION_TIMEOUT, "5000");

    try (Connection managementConnection = ConnectionFactory.connect(voltron.getConnectionURI(), properties)) {

      NmsEntityFactory nmsEntityFactory = new NmsEntityFactory(managementConnection, getClass().getSimpleName());
      NmsEntity nmsEntity = nmsEntityFactory.retrieveOrCreate(new NmsConfig());
      DefaultNmsService nmsService = new DefaultNmsService(nmsEntity);
      nmsService.setOperationTimeout(60, TimeUnit.SECONDS);

      // wait for the 2 megatron entities to be created
      int count = 0;
      while (!Thread.currentThread().isInterrupted() && count != N_SERVERS) {
        org.terracotta.management.model.cluster.Cluster cluster = nmsService.readTopology();
        count = (int) cluster.getSingleStripe()
            .serverEntityStream().filter(serverEntity -> serverEntity.getType().equals("com.mycila.megatron.server.entity.MegatronEntity"))
            .count();
        Thread.sleep(2_000);
      }
      assertThat(count, equalTo(N_SERVERS));

      FutureTask<List<Message>> task = new FutureTask<>(() -> nmsService.waitForMessage(message -> message.getType().equals("STATISTICS")));
      Thread t = new Thread(task);
      t.start();
      try {
        System.out.println(" - " + task.get(5, TimeUnit.SECONDS).stream().map(Object::toString).collect(Collectors.joining("\n - ")));
        fail("we shouldn't receive any stat from the nms entity");
      } catch (InterruptedException | ExecutionException e) {
        fail(e.getMessage());
      } catch (TimeoutException e) {
        // ok! we shouldn't receive any stat from the nms entity
      }
    }
  }

}
