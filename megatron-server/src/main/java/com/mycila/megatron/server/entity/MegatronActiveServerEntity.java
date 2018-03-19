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
import org.terracotta.management.model.cluster.Client;
import org.terracotta.management.model.cluster.Cluster;
import org.terracotta.management.model.cluster.Server;
import org.terracotta.management.model.cluster.ServerEntity;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.context.ContextContainer;
import org.terracotta.management.model.context.Contextual;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Mathieu Carbou
 */
class MegatronActiveServerEntity extends ActiveProxiedServerEntity<Void, Void, MegatronEntityCallback> implements MegatronEntity, ManagementExecutor, MegatronEntityCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(MegatronActiveServerEntity.class);

  private final ManagementService managementService;
  private final EntityManagementRegistry entityManagementRegistry;
  private final CapabilityManagementSupport capabilityManagementSupport;
  private final long consumerId;
  private final MegatronConfiguration megatronConfiguration;
  private final Collection<MegatronEventListener> listeners;
  private final MegatronClientDescriptor megatronClientDescriptor = new MegatronClientDescriptor();
  private final Set<Context> pendingStarts = ConcurrentHashMap.newKeySet();

  MegatronActiveServerEntity(ManagementService managementService, EntityManagementRegistry entityManagementRegistry, SharedEntityManagementRegistry sharedEntityManagementRegistry, MegatronConfiguration megatronConfiguration, Collection<MegatronEventListener> listeners) {
    this.entityManagementRegistry = Objects.requireNonNull(entityManagementRegistry);
    this.capabilityManagementSupport = new CombiningCapabilityManagementSupport(sharedEntityManagementRegistry, entityManagementRegistry);
    this.managementService = Objects.requireNonNull(managementService);
    this.consumerId = entityManagementRegistry.getMonitoringService().getConsumerId();
    this.megatronConfiguration = Objects.requireNonNull(megatronConfiguration);
    this.listeners = Objects.requireNonNull(listeners);
  }

  // ActiveProxiedServerEntity

  @Override
  public void destroy() {
    entityManagementRegistry.close();
    managementService.close();
    super.destroy();
  }

  @Override
  public void createNew() {
    super.createNew();
    entityManagementRegistry.entityCreated();
    entityManagementRegistry.refresh();
  }

  @Override
  public void loadExisting() {
    super.loadExisting();
    entityManagementRegistry.entityPromotionCompleted();
    entityManagementRegistry.refresh();
  }

  @Override
  protected void dumpState(StateDumpCollector dump) {
    dump.addState("consumerId", String.valueOf(consumerId));
    dump.addState("listeners", listeners);
  }

  // ManagementExecutor

  @Override
  public void executeManagementCallOnServer(String managementCallIdentifier, ContextualCall<?> call) {
    String serverName = call.getContext().get(Server.NAME_KEY);
    if (serverName == null) {
      throw new IllegalArgumentException("Bad context: " + call.getContext());
    }
    if (entityManagementRegistry.getMonitoringService().getServerName().equals(serverName)) {
      ContextualReturn<?> contextualReturn = capabilityManagementSupport.withCapability(call.getCapability())
          .call(call.getMethodName(), call.getReturnType(), call.getParameters())
          .on(call.getContext())
          .build()
          .execute()
          .getSingleResult();
      if (contextualReturn.hasExecuted()) {
        LOGGER.trace("[{}] executeManagementCallOnServer({}, {}): {}", consumerId, managementCallIdentifier, call, contextualReturn);
        entityManagementRegistry.getMonitoringService().answerManagementCall(managementCallIdentifier, contextualReturn);
      }
    } else {
      getMessenger().executeManagementCallOnPassive(managementCallIdentifier, call);
    }
  }

  @Override
  public void sendMessageToClients(Message message) {
    // hook on notifications here
    try {
      switch (message.getType()) {

        case "NOTIFICATION": {
          List<ContextualNotification> notifications = message.unwrap(ContextualNotification.class);
          for (ContextualNotification notification : notifications) {
            LOGGER.trace("[{}] onNotifications({}): {}", consumerId, notification.getType(), notification.getContext());

            switch (notification.getType()) {

              // start collecting stats on a new client registry
              case "ENTITY_REGISTRY_AVAILABLE": {
                needToStartStatisticCollector(notification.getContext());
                getMessenger().restartStatisticCollectors();
                break;
              }

              // start collecting stats on a new entity registry
              case "CLIENT_REGISTRY_AVAILABLE": {
                needToStartStatisticCollector(notification.getContext());
                getMessenger().restartStatisticCollectors();
                break;
              }
            }
          }
          sendNotificationToPlugins(notifications);
          break;
        }

        case "STATISTICS": {
          List<ContextualStatistics> statistics = message.unwrap(ContextualStatistics.class);
          for (ContextualStatistics statistic : statistics) {
            LOGGER.trace("[{}] onStatistics({}): {}", consumerId, statistic.size(), statistic.getContext());
          }
          sendStatisticsToPlugins(statistics);
          break;
        }
      }
    } catch (Exception e) {
      LOGGER.error("[{}] sendMessageToClients({}): {}", consumerId, message, e.getMessage(), e);
    }
  }

  @Override
  public void sendMessageToClient(Message message, ClientDescriptor to) {
    if (message.getType().equals("MANAGEMENT_CALL_RETURN")) {
      Context context = message.unwrap(Contextual.class).get(0).getContext();
      if (pendingStarts.removeIf(context::contains)) {
        LOGGER.trace("[{}] sendMessageToClient({}): statistic collector started.", consumerId, context);
      }
    }
  }

  // MegatronEntityCallback

  @Override
  public void executeManagementCallOnPassive(String managementCallIdentifier, ContextualCall<?> call) {
    throw new UnsupportedOperationException();
  }

  // called by IEntityMessenger
  @Override
  public void restartStatisticCollectors() {
    if (!pendingStarts.isEmpty()) {
      Cluster cluster = managementService.readTopology();
      pendingStarts.removeIf(context -> !restartStatisticCollector(cluster, context));
      if (!pendingStarts.isEmpty()) {
        // try again for pending contexts
        try {
          Thread.sleep(500);
          getMessenger().restartStatisticCollectors();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private boolean restartStatisticCollector(Cluster cluster, Context context) {
    Optional<? extends AbstractManageableNode<?>> o;
    if (context.contains(Client.KEY)) {
      o = cluster.getClient(context);
    } else {
      o = cluster.getServerEntity(context)
          .filter(serverEntity -> serverEntity.getType().equals(MegatronEntity.TYPE));
    }
    return o.filter(AbstractManageableNode::isManageable).map(node -> {
      ContextContainer contextContainer = node.getManagementRegistry().get().getContextContainer();
      Context ctx = node.getContext().with(contextContainer.getName(), contextContainer.getValue());
      LOGGER.trace("[{}] restartStatisticCollector({})", consumerId, ctx);
      return managementService.sendManagementCallRequest(
          megatronClientDescriptor,
          ctx,
          "StatisticCollectorCapability",
          "startStatisticCollector",
          Void.TYPE,
          new Parameter(megatronConfiguration.getStatisticCollectorInterval(), long.class.getName()),
          new Parameter(TimeUnit.MILLISECONDS, TimeUnit.class.getName()));
    }).isPresent();
  }

  private void needToStartStatisticCollector(Context context) {
    if (context.contains(Client.KEY) || MegatronEntity.TYPE.equals(context.get(ServerEntity.TYPE_KEY))) {
      LOGGER.trace("[{}] needToStartStatisticCollector({})", consumerId, context);
      pendingStarts.add(context);
    }
  }

  private void sendNotificationToPlugins(List<ContextualNotification> notifications) {
    if (!notifications.isEmpty()) {
      listeners.forEach(plugin -> {
        try {
          plugin.onNotifications(notifications);
        } catch (Exception e) {
          LOGGER.error("[{}] sendNotificationToPlugins({}): ", consumerId, plugin.getClass(), e.getMessage(), e);
        }
      });
    }
  }

  private void sendStatisticsToPlugins(List<ContextualStatistics> statistics) {
    if (!statistics.isEmpty()) {
      listeners.forEach(plugin -> {
        try {
          plugin.onStatistics(statistics);
        } catch (Exception e) {
          LOGGER.error("[{}] sendStatisticsToPlugins({}): ", consumerId, plugin.getClass(), e.getMessage(), e);
        }
      });
    }
  }

}
