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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.management.model.call.ContextualCall;
import org.terracotta.management.model.call.ContextualReturn;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.registry.CapabilityManagementSupport;
import org.terracotta.management.registry.CombiningCapabilityManagementSupport;
import org.terracotta.management.service.monitoring.EntityManagementRegistry;
import org.terracotta.management.service.monitoring.SharedEntityManagementRegistry;
import org.terracotta.voltron.proxy.server.PassiveProxiedServerEntity;

import java.util.Objects;

/**
 * @author Mathieu Carbou
 */
class MegatronPassiveServerEntity extends PassiveProxiedServerEntity implements MegatronEntity, MegatronEntityCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(MegatronPassiveServerEntity.class);

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
    entityManagementRegistry.entityCreated();
    entityManagementRegistry.refresh();
  }

  @Override
  protected void dumpState(StateDumpCollector dump) {
    dump.addState("consumerId", String.valueOf(entityManagementRegistry.getMonitoringService().getConsumerId()));
  }

  // NmsCallback

  @Override
  public void executeManagementCallOnPassive(String managementCallIdentifier, ContextualCall<?> call) {
    String serverName = call.getContext().get(Server.NAME_KEY);
    if (entityManagementRegistry.getMonitoringService().getServerName().equals(serverName)) {
      ContextualReturn<?> contextualReturn = capabilityManagementSupport.withCapability(call.getCapability())
          .call(call.getMethodName(), call.getReturnType(), call.getParameters())
          .on(call.getContext())
          .build()
          .execute()
          .getSingleResult();
      LOGGER.trace("[{}] executeManagementCallOnPassive({}, {}): {}", entityManagementRegistry.getMonitoringService().getConsumerId(), managementCallIdentifier, call, contextualReturn);
      if (contextualReturn.hasExecuted()) {
        entityManagementRegistry.getMonitoringService().answerManagementCall(managementCallIdentifier, contextualReturn);
      }
    }
  }

  @Override
  public void restartStatisticCollectors() {
    throw new UnsupportedOperationException();
  }

}
