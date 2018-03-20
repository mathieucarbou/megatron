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
import org.terracotta.management.model.notification.ContextualNotification;
import org.terracotta.management.model.stats.ContextualStatistics;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static com.mycila.megatron.test.Replay.Option.FASTER;
import static com.mycila.megatron.test.Replay.Option.NORMAL_SPEED;

/**
 * @author Mathieu Carbou
 */
public class Replay {

  public enum Option {NORMAL_SPEED, FASTER, FASTEST}

  private final URL recording;
  private final MegatronEventListener listener;

  public Replay(File recording, MegatronEventListener listener) {
    try {
      this.recording = recording.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(recording.toString() + " : " + e.getMessage(), e);
    }
    this.listener = listener;
  }

  public Replay(URL recording, MegatronEventListener listener) {
    this.recording = recording;
    this.listener = listener;
  }

  public Future<Void> start() {
    return start(NORMAL_SPEED);
  }

  @SuppressWarnings("unchecked")
  public Future<Void> start(Option... options) {
    EnumSet<Option> opts = EnumSet.copyOf(Arrays.asList(options));
    FutureTask<Void> task = new FutureTask<>(() -> {
      try (ObjectInputStream ois = new ObjectInputStream(recording.openStream())) {
        long time = ois.readLong();
        while (!Thread.currentThread().isInterrupted()) {
          Event event = (Event) ois.readObject();
          long sleep = 0;
          if (opts.contains(NORMAL_SPEED)) {
            sleep = event.getTime() - time;
          } else if (opts.contains(FASTER)) {
            sleep = (event.getTime() - time) / 2;
          }
          if (sleep > 0) {
            Thread.sleep(sleep);
          }
          time = event.getTime();
          switch (event.getType()) {
            case EOF:
              // finish thread
              return;
            case STATISTICS:
              listener.onStatistics((List<ContextualStatistics>) event.getObject());
              break;
            case NOTIFICATIONS:
              listener.onNotifications((List<ContextualNotification>) event.getObject());
              break;
            default:
              throw new AssertionError(event.getType());
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }
    }, null);
    Thread thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
    return task;
  }

}
