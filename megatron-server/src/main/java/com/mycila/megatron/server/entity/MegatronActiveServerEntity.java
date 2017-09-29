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

import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.MegatronEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.entity.ClientDescriptor;
import org.terracotta.entity.StateDumpCollector;
import org.terracotta.management.model.call.ContextualCall;
import org.terracotta.management.model.call.ContextualReturn;
import org.terracotta.management.model.call.Parameter;
import org.terracotta.management.model.cluster.AbstractManageableNode;
import org.terracotta.management.model.cluster.Cluster;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.cluster.Stripe;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.context.ContextContainer;
import org.terracotta.management.model.message.Message;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.management.registry.CapabilityManagementSupport;
import org.terracotta.management.registry.CombiningCapabilityManagementSupport;
import org.terracotta.management.service.monitoring.EntityManagementRegistry;
import org.terracotta.management.service.monitoring.ManagementExecutor;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.management.service.monitoring.SharedEntityManagementRegistry;
import org.terracotta.voltron.proxy.server.ActiveProxiedServerEntity;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author Mathieu Carbou
 */
class MegatronActiveServerEntity extends ActiveProxiedServerEntity<Void, Void, ManagementCallMessenger> implements MegatronEntity, ManagementExecutor, ManagementCallMessenger {

  private static final Logger LOGGER = LoggerFactory.getLogger(MegatronActiveServerEntity.class);

  private final ManagementService managementService;
  private final EntityManagementRegistry entityManagementRegistry;
  private final CapabilityManagementSupport capabilityManagementSupport;
  private final long consumerId;
  private final MegatronConfiguration megatronConfiguration;
  private final Collection<MegatronEventListener> listeners;
  private final MegatronClientDescriptor megatronClientDescriptor = new MegatronClientDescriptor();

  MegatronActiveServerEntity(ManagementService managementService, EntityManagementRegistry entityManagementRegistry, SharedEntityManagementRegistry sharedEntityManagementRegistry, MegatronConfiguration megatronConfiguration, Collection<MegatronEventListener> listeners) {
    this.entityManagementRegistry = Objects.requireNonNull(entityManagementRegistry);
    this.capabilityManagementSupport = new CombiningCapabilityManagementSupport(sharedEntityManagementRegistry, entityManagementRegistry);
    this.managementService = Objects.requireNonNull(managementService);
    this.consumerId = entityManagementRegistry.getMonitoringService().getConsumerId();
    this.megatronConfiguration = Objects.requireNonNull(megatronConfiguration);
    this.listeners = Objects.requireNonNull(listeners);
  }

  ///////////////////////////////
  // ActiveProxiedServerEntity //
  ///////////////////////////////

  @Override
  public void destroy() {
    entityManagementRegistry.close();
    managementService.close();
    super.destroy();
  }

  @Override
  public void createNew() {
    super.createNew();
    entityManagementRegistry.refresh();
  }

  @Override
  public void loadExisting() {
    super.loadExisting();
    entityManagementRegistry.cleanupPreviousPassiveStates();
    entityManagementRegistry.refresh();
  }

  @Override
  protected void dumpState(StateDumpCollector dump) {
    dump.addState("consumerId", String.valueOf(consumerId));
    dump.addState("listeners", listeners);
  }

  ///////////////////////////////////////////////////////////////////
  // ManagementCallMessenger: execute callbacks from the Messenger //
  ///////////////////////////////////////////////////////////////////

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
    if (context.contains(Stripe.KEY)) {
      context = context.with(Stripe.KEY, "SINGLE");
    }
    managementService.sendManagementCallRequest(megatronClientDescriptor, context, capabilityName, methodName, returnType, parameters);
  }

  @Override
  public void callbackToSendNotification(ContextualNotification notification) {
    listeners.forEach(plugin -> {
      try {
        plugin.onNotification(notification);
      } catch (Exception e) {
        LOGGER.error("[{}] onNotification({}): ", consumerId, plugin.getClass(), e.getMessage(), e);
      }
    });
  }

  @Override
  public void callbackToSendStatistics(ContextualStatistics statistics) {
    listeners.forEach(plugin -> {
      try {
        plugin.onStatistics(statistics);
      } catch (Exception e) {
        LOGGER.error("[{}] onStatistics({}): ", consumerId, plugin.getClass(), e.getMessage(), e);
      }
    });
  }

  @Override
  public void unSchedule() {
    throw new UnsupportedOperationException();
  }

  ////////////////////////////////////////////////////////
  // ManagementExecutor: callback of monitoring service //
  ////////////////////////////////////////////////////////

  @Override
  public void executeManagementCallOnServer(String managementCallIdentifier, ContextualCall<?> call) {
    getMessenger().callbackToExecuteManagementCall(managementCallIdentifier, call);
  }

  @Override
  public void sendMessageToClients(Message message) {
    try {
      switch (message.getType()) {

        case "NOTIFICATION": {
          message.unwrap(ContextualNotification.class).forEach(notification -> {
            LOGGER.trace("[{}] onNotification({}): {}", consumerId, notification.getType(), notification.getContext());

            switch (notification.getType()) {

              // start collecting stats on a new client registry
              case "ENTITY_REGISTRY_AVAILABLE": {
                executeOnAllManageables(this::startStatisticCollector);
                break;
              }

              // start collecting stats on a new entity registry
              case "CLIENT_REGISTRY_AVAILABLE": {
                executeOnAllManageables(this::startStatisticCollector);
                break;
              }
            }

            getMessenger().callbackToSendNotification(notification);
          });
          break;
        }

        case "STATISTICS": {
          message.unwrap(ContextualStatistics.class).forEach(statistics -> {
            LOGGER.trace("[{}] onStatistics({}): {}", consumerId, statistics.size(), statistics.getContext());
            getMessenger().callbackToSendStatistics(statistics);
          });
          break;
        }
      }
    } catch (Exception e) {
      LOGGER.error("[{}] sendMessageToClients({}): {}", consumerId, message, e.getMessage(), e);
    }
  }

  @Override
  public void sendMessageToClient(Message message, ClientDescriptor to) {
    // do nothing: notifications are sent to every clients
  }

  private void executeOnAllManageables(Consumer<AbstractManageableNode<?>> consumer) {
    Cluster cluster = managementService.readTopology();
    Stream.concat(cluster.serverEntityStream(), cluster.clientStream())
        .filter(AbstractManageableNode::isManageable)
        .filter(source -> source.getManagementRegistry().get().getCapability("StatisticCollectorCapability").isPresent())
        .forEach(consumer);
  }

  private void startStatisticCollector(AbstractManageableNode<?> source) {
    LOGGER.trace("[{}] startStatisticCollector({})", consumerId, source);
    source.getManagementRegistry().ifPresent(managementRegistry -> {
      ContextContainer contextContainer = managementRegistry.getContextContainer();
      Context context = source.getContext().with(contextContainer.getName(), contextContainer.getValue());
      getMessenger().callbackToSendManagementCall(
          context,
          "StatisticCollectorCapability",
          "startStatisticCollector",
          Void.TYPE,
          new Parameter(megatronConfiguration.getStatisticCollectorInterval(), long.class.getName()),
          new Parameter(TimeUnit.MILLISECONDS, TimeUnit.class.getName()));
    });
  }

}
