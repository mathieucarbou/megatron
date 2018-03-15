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
package com.mycila.megatron.udp;

import com.mycila.megatron.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mycila.megatron.Utils.closeSilently;

public final class NonBlockingUdpClient implements Client {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingUdpClient.class);

  private final BlockingQueue<List<String>> batches;
  private final DatagramSocket channel;
  private final String hostname;
  private final int port;
  private final Thread sender;

  private volatile InetSocketAddress cachedTarget;
  private volatile boolean closed;

  public NonBlockingUdpClient(String hostname, int port, int queueSize, ThreadFactory threadFactory) {
    this.batches = new LinkedBlockingQueue<>(queueSize);
    this.hostname = hostname;
    this.port = port;
    try {
      this.channel = new DatagramSocket();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to start UDP client", e);
    }
    this.sender = threadFactory.newThread(() -> {
      while (!closed && !Thread.currentThread().isInterrupted()) {
        try {
          List<String> messages = batches.poll(1, TimeUnit.SECONDS);
          if (messages != null) {
            drainAndSend(messages);
          }
        } catch (InterruptedException e) {
          drainAndSend(Collections.emptyList());
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
      drainAndSend(Collections.emptyList());
      closeSilently(channel);
    }
  }

  @Override
  public void send(List<String> messages) {
    if (!closed && !messages.isEmpty()) {
      batches.offer(messages);
    }
  }

  private void drainAndSend(List<String> polled) {
    List<List<String>> drained;
    int size = batches.size();
    if (size > 0) {
      drained = new ArrayList<>(1 + size);
      drained.add(polled);
      batches.drainTo(drained);
    } else {
      drained = Collections.singletonList(polled);
    }
    if (LOGGER.isTraceEnabled()) {
      String message = drained.stream()
          .flatMap(Collection::stream)
          .collect(Collectors.joining("\n"));
      LOGGER.trace("[{}:{}] UDP > \n{}", hostname, port, message);
    }
    try {
      InetSocketAddress target = getTarget();
      for (List<String> messages : drained) {
        for (String message : messages) {
          byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
          DatagramPacket packet = new DatagramPacket(bytes, bytes.length, target);
          channel.send(packet);
        }
      }
    } catch (IOException e) {
      LOGGER.warn("[{}:{}] UDP ERROR: {}", hostname, port, e.getMessage(), e);
    }
  }

  private InetSocketAddress getTarget() throws UnknownHostException {
    if (cachedTarget == null) {
      cachedTarget = new InetSocketAddress(InetAddress.getByName(hostname), port);
    }
    return cachedTarget;
  }

}
