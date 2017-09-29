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

import java.io.Closeable;

/**
 * A plugin is responsible to send statistics to a
 * Network Management System (Librato, Datadog, etc)
 * or expose them through a REST api.
 * <p>
 * Plugins are only initialized on the active servers
 * and receive data from the whole stripe.
 *
 * @author Mathieu Carbou
 */
@CommonComponent
public interface MegatronPlugin extends MegatronEventListener, Closeable {

  /**
   * Called at plugin initialization time.
   * Can be used to start a socket or prepare the streaming and access the plugin configuration.
   */
  default void init(MegatronConfiguration configuration, MegatronApi api) {}

  /**
   * @return True is this plugin must be enabled
   */
  default boolean isEnable() { return false; }

  /**
   * Close the plugin
   */
  @Override
  default void close() {}

}
