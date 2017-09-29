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

import org.terracotta.entity.ClientDescriptor;
import org.terracotta.entity.ClientSourceId;

/**
 * @author Mathieu Carbou
 */
class MegatronClientDescriptor implements ClientDescriptor, ClientSourceId {

  private final Object object = new Object();

  @Override
  public ClientSourceId getSourceId() {
    return this;
  }

  @Override
  public long toLong() {
    return object.hashCode();
  }

  @Override
  public boolean matches(ClientDescriptor cd) {
    return cd == this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MegatronClientDescriptor that = (MegatronClientDescriptor) o;
    return object.equals(that.object);
  }

  @Override
  public int hashCode() {
    return object.hashCode();
  }

}
