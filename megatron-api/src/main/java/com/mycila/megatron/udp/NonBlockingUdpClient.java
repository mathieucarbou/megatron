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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.mycila.megatron.Utils.closeSilently;

public final class NonBlockingUdpClient implements Client {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingUdpClient.class);

  private final InetSocketAddress target;
  private final BlockingQueue<String> messages;
  private final DatagramChannel channel;
  private final Thread sender;

  private volatile boolean closed;

  public NonBlockingUdpClient(String hostname, int port) {
    this(hostname, port, Integer.MAX_VALUE);
  }

  public NonBlockingUdpClient(String hostname, int port, int queueSize) {
    target = resolve(hostname, port);
    messages = new LinkedBlockingQueue<>(queueSize);

    try {
      channel = DatagramChannel.open();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to start UDP client", e);
    }

    sender = new Thread(() -> {
      while (!closed && !Thread.currentThread().isInterrupted()) {
        try {
          String message = messages.poll(1, TimeUnit.SECONDS);
          if (message != null) {
            internalSend(message);
          }
        } catch (InterruptedException e) {
          tryDequeue();
          closed = true;
          closeSilently(channel);
        } catch (IOException e) {
          LOGGER.warn("[{}:{}] ERR: {}", hostname, port, e.getMessage(), e);
        }
      }
    }, NonBlockingUdpClient.class.getSimpleName() + "[" + hostname + ":" + port + "]");
    sender.start();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;

      try {
        sender.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      tryDequeue();

      closeSilently(channel);
    }
  }

  public void send(List<String> messages) {
    if (!closed) {
      for (String message : messages) {
        this.messages.offer(message);
      }
    }
  }

  private void internalSend(String message) throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("[{}:{}] > {}", target.getHostName(), target.getPort(), message);
    }
    ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
    channel.send(buffer, target);
  }

  private void tryDequeue() {
    try {
      while (!messages.isEmpty()) {
        internalSend(messages.poll());
      }
    } catch (IOException ignored) {
    }
  }

  private static InetSocketAddress resolve(String hostname, int port) {
    try {
      return new InetSocketAddress(InetAddress.getByName(hostname), port);
    } catch (UnknownHostException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void main(String[] args) {
    NonBlockingUdpClient client = new NonBlockingUdpClient(args[0], Integer.parseInt(args[1]));
    UUID uuid = UUID.randomUUID();
    for (int i = 0; i < 100; i++) {
      client.send(i + " " + uuid);
      //Thread.sleep(1_000);
    }
    client.close();
  }

}
