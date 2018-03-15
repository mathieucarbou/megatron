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
package com.mycila.megatron.server.service;

import com.mycila.megatron.MegatronApi;
import org.terracotta.management.model.cluster.Cluster;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.monitoring.PlatformService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
class ServerMegatronApi implements MegatronApi {

  private final ManagementService managementService;
  private final PlatformService platformService;
  private final String serverName;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;

  ServerMegatronApi(ManagementService managementService, PlatformService platformService, String serverName, ExecutorService executorService, ScheduledExecutorService scheduledExecutorService) {
    this.managementService = Objects.requireNonNull(managementService);
    this.platformService = Objects.requireNonNull(platformService);
    this.serverName = Objects.requireNonNull(serverName);
    this.executorService = Objects.requireNonNull(executorService);
    this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
  }

  @Override
  public Cluster readLiveTopology() {
    return managementService.readTopology();
  }

  @Override
  public void dumpPlatformState() {
    platformService.dumpPlatformState();
  }

  @Override
  public String getPlatformXMLConfiguration() {
    try (InputStream inputStream = platformService.getPlatformConfiguration(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      int c;
      while ((c = inputStream.read()) != -1) {
        baos.write(c);
      }
      return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  @Override
  public String getServerName() {
    return serverName;
  }

  @Override
  public Executor getAsyncExecutor() {
    return executorService;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return scheduledExecutorService.schedule(command, delay, unit);
  }

}
