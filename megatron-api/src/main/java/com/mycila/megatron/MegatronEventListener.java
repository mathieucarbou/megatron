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
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.List;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public interface MegatronEventListener {

  /**
   * Callback called when a new stripe notification arrives.
   */
  default void onNotifications(List<ContextualNotification> notifications) {}

  /**
   * Callback called when a new set of statistics has been collected from a manageable element on teh stripe.
   */
  default void onStatistics(List<ContextualStatistics> contextualStatistics) {}

}
