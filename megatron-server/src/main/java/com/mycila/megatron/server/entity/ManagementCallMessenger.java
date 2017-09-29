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
package com.mycila.megatron.server.entity;

import org.terracotta.management.model.call.ContextualCall;
import org.terracotta.management.model.call.Parameter;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.voltron.proxy.ConcurrencyStrategy;
import org.terracotta.voltron.proxy.ExecutionStrategy;
import org.terracotta.voltron.proxy.server.Messenger;

import static org.terracotta.voltron.proxy.ExecutionStrategy.Location.ACTIVE;
import static org.terracotta.voltron.proxy.ExecutionStrategy.Location.BOTH;

/**
 * @author Mathieu Carbou
 */
public interface ManagementCallMessenger extends Messenger {

  @ConcurrencyStrategy(key = ConcurrencyStrategy.UNIVERSAL_KEY)
  @ExecutionStrategy(location = BOTH)
  void callbackToExecuteManagementCall(String managementCallIdentifier, ContextualCall<?> call);

  @ConcurrencyStrategy(key = ConcurrencyStrategy.UNIVERSAL_KEY)
  @ExecutionStrategy(location = ACTIVE)
  void callbackToSendManagementCall(Context context, String capabilityName, String methodName, Class<?> returnType, Parameter... parameters);

  @ConcurrencyStrategy(key = ConcurrencyStrategy.UNIVERSAL_KEY)
  @ExecutionStrategy(location = ACTIVE)
  void callbackToSendNotification(ContextualNotification notification);

  @ConcurrencyStrategy(key = ConcurrencyStrategy.UNIVERSAL_KEY)
  @ExecutionStrategy(location = ACTIVE)
  void callbackToSendStatistics(ContextualStatistics statistics);

}
