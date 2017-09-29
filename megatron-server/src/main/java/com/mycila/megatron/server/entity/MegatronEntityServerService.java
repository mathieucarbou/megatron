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
import com.mycila.megatron.server.service.MegatronServiceConfiguration;
import com.tc.classloader.PermanentEntity;
import org.terracotta.entity.BasicServiceConfiguration;
import org.terracotta.entity.ConfigurationException;
import org.terracotta.entity.ServiceException;
import org.terracotta.entity.ServiceRegistry;
import org.terracotta.management.service.monitoring.EntityManagementRegistry;
import org.terracotta.management.service.monitoring.ManagementRegistryConfiguration;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.management.service.monitoring.ManagementServiceConfiguration;
import org.terracotta.management.service.monitoring.SharedEntityManagementRegistry;
import org.terracotta.monitoring.PlatformService;
import org.terracotta.voltron.proxy.SerializationCodec;
import org.terracotta.voltron.proxy.server.ProxyServerEntityService;

import java.util.Collection;
import java.util.Objects;

/**
 * @author Mathieu Carbou
 */
@PermanentEntity(type = MegatronEntity.TYPE, names = "MegatronEntity")
public class MegatronEntityServerService extends ProxyServerEntityService<Void, Void, Void, ManagementCallMessenger> {

  public MegatronEntityServerService() {
    super(MegatronEntity.class, Void.class, null, null, null, ManagementCallMessenger.class);
    setCodec(new SerializationCodec());
  }

  @Override
  public MegatronActiveServerEntity createActiveEntity(ServiceRegistry registry, Void configuration) throws ConfigurationException {
    try {
      ManagementService managementService = Objects.requireNonNull(registry.getService(new ManagementServiceConfiguration()));
      PlatformService platformService = Objects.requireNonNull(registry.getService(new BasicServiceConfiguration<>(PlatformService.class)));
      EntityManagementRegistry entityManagementRegistry = Objects.requireNonNull(registry.getService(new ManagementRegistryConfiguration(registry, true, true)));
      SharedEntityManagementRegistry sharedEntityManagementRegistry = Objects.requireNonNull(registry.getService(new BasicServiceConfiguration<>(SharedEntityManagementRegistry.class)));
      MegatronConfiguration megatronConfiguration = Objects.requireNonNull(registry.getService(new BasicServiceConfiguration<>(MegatronConfiguration.class)));
      Collection<MegatronEventListener> listeners = registry.getServices(new MegatronServiceConfiguration(managementService, platformService));
      MegatronActiveServerEntity entity = new MegatronActiveServerEntity(managementService, entityManagementRegistry, sharedEntityManagementRegistry, megatronConfiguration, listeners);
      managementService.setManagementExecutor(entity);
      return entity;
    } catch (ServiceException e) {
      throw new ConfigurationException("Unable to retrieve service: " + e.getMessage(), e);
    }
  }

  @Override
  protected MegatronPassiveServerEntity createPassiveEntity(ServiceRegistry registry, Void configuration) throws ConfigurationException {
    try {
      EntityManagementRegistry entityManagementRegistry = Objects.requireNonNull(registry.getService(new ManagementRegistryConfiguration(registry, false, true)));
      SharedEntityManagementRegistry sharedEntityManagementRegistry = Objects.requireNonNull(registry.getService(new BasicServiceConfiguration<>(SharedEntityManagementRegistry.class)));
      return new MegatronPassiveServerEntity(entityManagementRegistry, sharedEntityManagementRegistry);
    } catch (ServiceException e) {
      throw new ConfigurationException("Unable to retrieve service: " + e.getMessage(), e);
    }
  }

  @Override
  public long getVersion() {
    return 1;
  }

  @Override
  public boolean handlesEntityType(String typeName) {
    return MegatronEntity.TYPE.equals(typeName);
  }

}
