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
package com.mycila.megatron.plugins.recorder;

import com.mycila.megatron.AbstractMegatronPlugin;
import com.mycila.megatron.Config;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.Namespace;
import com.mycila.megatron.Utils;
import com.mycila.megatron.test.Event;
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author Mathieu Carbou
 */
@Namespace("megatron.recorder")
public class MegatronRecorderPlugin extends AbstractMegatronPlugin {

  @Config(required = true) File output;

  private final BlockingQueue<Event> events = new LinkedBlockingDeque<>();

  private volatile Thread thread;
  private volatile ObjectOutputStream oos;

  @Override
  protected void enable(MegatronConfiguration configuration) {
    try {
      oos = new ObjectOutputStream(new FileOutputStream(output, false));
      oos.writeLong(System.currentTimeMillis());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    thread = getApi().getThreadFactory().newThread(() -> {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          try {
            Event event = events.take();
            oos.writeObject(event);
          } catch (InterruptedException e) {
            drain();
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } finally {
        Utils.closeSilently(oos);
      }
    });
    logger.info("Recording events...");
    thread.start();
  }

  @Override
  public void onNotifications(List<ContextualNotification> notifications) {
    if (enable) {
      logger.trace("onNotifications({})", notifications.size());
      events.offer(new Event(System.currentTimeMillis(), Event.Type.NOTIFICATIONS, new ArrayList<>(notifications)));
    }
  }

  @Override
  public void onStatistics(List<ContextualStatistics> contextualStatistics) {
    if (enable) {
      logger.trace("onNotifications({})", contextualStatistics.size());
      events.offer(new Event(System.currentTimeMillis(), Event.Type.STATISTICS, new ArrayList<>(contextualStatistics)));
    }
  }

  @Override
  public void close() {
    if (enable) {
      enable = false;
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void drain() throws IOException {
    logger.info("Finishing up event recording...");
    List<Event> drained = new ArrayList<>(events.size());
    events.drainTo(drained);
    for (Event event : drained) {
      oos.writeObject(event);
    }
    oos.writeObject(new Event(System.currentTimeMillis(), Event.Type.EOF, null));
  }

}
