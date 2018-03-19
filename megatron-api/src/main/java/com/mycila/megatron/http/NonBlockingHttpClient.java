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
package com.mycila.megatron.http;

import com.mycila.megatron.Client;
import com.tc.classloader.CommonComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommonComponent
public final class NonBlockingHttpClient implements Client {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingHttpClient.class);

  private final BlockingQueue<String> batches;
  private final Thread sender;
  private final URL url;

  private volatile boolean closed;

  public NonBlockingHttpClient(URL url, int queueSize, ThreadFactory threadFactory) {
    this.url = url;
    this.batches = new LinkedBlockingQueue<>(queueSize);
    this.sender = threadFactory.newThread(() -> {
      while (!closed && !Thread.currentThread().isInterrupted()) {
        try {
          Optional<String> message = Optional.ofNullable(batches.poll(1, TimeUnit.SECONDS));
          drainAndSend(message);
        } catch (InterruptedException e) {
          drainAndSend(Optional.empty());
          closed = true;
        }
      }
    });
    this.sender.start();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      LOGGER.info("Closing...");
      try {
        sender.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      drainAndSend(Optional.empty());
    }
  }

  @Override
  public void send(Stream<String> messages) {
    if (!closed) {
      batches.offer(messages.collect(Collectors.joining("\n")));
    }
  }

  private void drainAndSend(Optional<String> polled) {
    List<String> drained;
    int size = batches.size();
    if (size > 0) {
      drained = new ArrayList<>(1 + size);
      polled.ifPresent(drained::add);
      batches.drainTo(drained);
    } else {
      drained = polled.map(Collections::singletonList).orElse(Collections.emptyList());
    }
    if (!drained.isEmpty()) {
      String message = drained.stream()
          .collect(Collectors.joining("\n")) + "\n";
      Http.post(url, message);
    }
  }

}
