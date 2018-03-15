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
package com.mycila.megatron;

import com.tc.classloader.CommonComponent;
import org.terracotta.management.model.cluster.Cluster;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public interface MegatronApi {

  Executor getAsyncExecutor();

  ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

  Cluster readLiveTopology();

  void dumpPlatformState();

  String getPlatformXMLConfiguration();

  String getServerName();

}
