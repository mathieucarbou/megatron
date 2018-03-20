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
package com.mycila.megatron.test;

import com.mycila.megatron.MegatronApi;
import org.terracotta.management.model.cluster.Cluster;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
public class MegatronTestApi implements MegatronApi, Closeable {

  private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

  @Override
  public ThreadFactory getThreadFactory() {
    return Thread::new;
  }

  @Override
  public Executor getAsyncExecutor() {
    return executorService;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return scheduledExecutorService.scheduleWithFixedDelay(command, delay, delay, unit);
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
    return "";
  }

  @Override
  public String getNodeName() {
    return "";
  }

  @Override
  public void close() {
    executorService.shutdown();
    scheduledExecutorService.shutdown();
  }

}
