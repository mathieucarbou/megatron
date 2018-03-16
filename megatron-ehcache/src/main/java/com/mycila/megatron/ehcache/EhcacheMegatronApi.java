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
package com.mycila.megatron.ehcache;

import com.mycila.megatron.MegatronApi;
import com.mycila.megatron.MegatronConfiguration;
import org.terracotta.management.model.cluster.Cluster;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
class EhcacheMegatronApi implements MegatronApi {

  private final String cmName;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final ThreadFactory threadFactory;
  private final MegatronConfiguration configuration;

  EhcacheMegatronApi(String cmName, ExecutorService executorService, ScheduledExecutorService scheduledExecutorService, ThreadFactory threadFactory, MegatronConfiguration configuration) {
    this.cmName = Objects.requireNonNull(cmName);
    this.executorService = Objects.requireNonNull(executorService);
    this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
    this.threadFactory = Objects.requireNonNull(threadFactory);
    this.configuration = Objects.requireNonNull(configuration);
  }

  @Override
  public Cluster readLiveTopology() {
    return Cluster.create();
  }

  @Override
  public void dumpState() {
  }

  @Override
  public String getConfiguration() {
    return configuration.toString();
  }

  @Override
  public String getNodeName() {
    return cmName;
  }

  @Override
  public ThreadFactory getThreadFactory() {
    return threadFactory;
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
