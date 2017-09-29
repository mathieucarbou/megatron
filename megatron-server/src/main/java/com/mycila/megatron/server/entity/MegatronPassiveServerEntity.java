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

import org.terracotta.entity.StateDumpCollector;
import org.terracotta.management.model.call.ContextualCall;
import org.terracotta.management.model.call.ContextualReturn;
import org.terracotta.management.model.call.Parameter;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.management.registry.CapabilityManagementSupport;
import org.terracotta.management.registry.CombiningCapabilityManagementSupport;
import org.terracotta.management.service.monitoring.EntityManagementRegistry;
import org.terracotta.management.service.monitoring.SharedEntityManagementRegistry;
import org.terracotta.voltron.proxy.server.PassiveProxiedServerEntity;

import java.util.Objects;

/**
 * @author Mathieu Carbou
 */
class MegatronPassiveServerEntity extends PassiveProxiedServerEntity implements MegatronEntity, ManagementCallMessenger {

  private final EntityManagementRegistry entityManagementRegistry;
  private final CapabilityManagementSupport capabilityManagementSupport;

  MegatronPassiveServerEntity(EntityManagementRegistry entityManagementRegistry, SharedEntityManagementRegistry sharedEntityManagementRegistry) {
    this.entityManagementRegistry = Objects.requireNonNull(entityManagementRegistry);
    this.capabilityManagementSupport = new CombiningCapabilityManagementSupport(sharedEntityManagementRegistry, entityManagementRegistry);
  }

  ////////////////////////////////
  // PassiveProxiedServerEntity //
  ////////////////////////////////

  @Override
  public void destroy() {
    entityManagementRegistry.close();
    super.destroy();
  }

  @Override
  public void createNew() {
    super.createNew();
    entityManagementRegistry.refresh();
  }

  @Override
  protected void dumpState(StateDumpCollector dump) {
    dump.addState("consumerId", String.valueOf(entityManagementRegistry.getMonitoringService().getConsumerId()));
  }

  /////////////////////////////
  // ManagementCallMessenger //
  /////////////////////////////

  @Override
  public void callbackToExecuteManagementCall(String managementCallIdentifier, ContextualCall<?> call) {
    String serverName = call.getContext().get(Server.NAME_KEY);
    if (entityManagementRegistry.getMonitoringService().getServerName().equals(serverName)) {
      ContextualReturn<?> contextualReturn = capabilityManagementSupport.withCapability(call.getCapability())
          .call(call.getMethodName(), call.getReturnType(), call.getParameters())
          .on(call.getContext())
          .build()
          .execute()
          .getSingleResult();
      entityManagementRegistry.getMonitoringService().answerManagementCall(managementCallIdentifier, contextualReturn);
    }
  }

  @Override
  public void callbackToSendManagementCall(Context context, String capabilityName, String methodName, Class<?> returnType, Parameter... parameters) {
    throw new UnsupportedOperationException("Cannot be called on a passive server");
  }

  @Override
  public void callbackToSendNotification(ContextualNotification notification) {
    throw new UnsupportedOperationException("Cannot be called on a passive server");
  }

  @Override
  public void callbackToSendStatistics(ContextualStatistics statistics) {
    throw new UnsupportedOperationException("Cannot be called on a passive server");
  }

  @Override
  public void unSchedule() {
    throw new UnsupportedOperationException("Cannot be called on a passive server");
  }

}
