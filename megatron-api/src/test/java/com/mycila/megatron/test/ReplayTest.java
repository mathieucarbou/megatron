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
package com.mycila.megatron.test;

import com.mycila.megatron.MegatronEventListener;
import org.junit.Test;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mycila.megatron.test.Replay.Option.FASTEST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mathieu Carbou
 */
public class ReplayTest {

  @Test
  public void test_replay() throws ExecutionException, InterruptedException {
    AtomicInteger notifs = new AtomicInteger();
    AtomicInteger stats = new AtomicInteger();

    Replay replay = new Replay(getClass().getResource("/com/mycila/megatron/test/server.bin"), new MegatronEventListener() {
      @Override
      public void onNotifications(List<ContextualNotification> notifications) {
        notifs.incrementAndGet();
      }

      @Override
      public void onStatistics(List<ContextualStatistics> contextualStatistics) {
        stats.incrementAndGet();
      }
    });

    Future<Void> end = replay.start(FASTEST);

    end.get();

    assertThat(notifs.get()).isEqualTo(48);
    assertThat(stats.get()).isEqualTo(54);
  }
}
