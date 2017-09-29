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

import com.mycila.megatron.MegatronEventListener;
import com.tc.classloader.CommonComponent;
import org.terracotta.entity.ServiceConfiguration;
import org.terracotta.management.service.monitoring.ManagementService;
import org.terracotta.monitoring.PlatformService;

import java.util.Objects;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class MegatronServiceConfiguration implements ServiceConfiguration<MegatronEventListener> {

  private final ManagementService managementService;
  private final PlatformService platformService;

  public MegatronServiceConfiguration(ManagementService managementService, PlatformService platformService) {
    this.managementService = Objects.requireNonNull(managementService);
    this.platformService = Objects.requireNonNull(platformService);
  }

  @Override
  public Class<MegatronEventListener> getServiceType() {
    return MegatronEventListener.class;
  }

  public ManagementService getManagementService() {
    return managementService;
  }

  public PlatformService getPlatformService() {
    return platformService;
  }

}
