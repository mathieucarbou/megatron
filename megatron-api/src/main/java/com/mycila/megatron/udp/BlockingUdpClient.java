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
import com.tc.classloader.CommonComponent;
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
import java.util.stream.Stream;

import static com.mycila.megatron.Utils.closeSilently;

@CommonComponent
public final class BlockingUdpClient implements Client {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlockingUdpClient.class);

  private final DatagramSocket channel;
  private final String hostname;
  private final int port;

  private volatile InetSocketAddress cachedTarget;
  private volatile boolean closed;

  public BlockingUdpClient(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
    try {
      this.channel = new DatagramSocket();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to start UDP client", e);
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      LOGGER.info("Closing...");
      closeSilently(channel);
    }
  }

  @Override
  public void send(Stream<String> messages) {
    if (!closed) {
      InetSocketAddress target;
      try {
        target = getTarget();
      } catch (IOException e) {
        LOGGER.warn("[{}:{}] UDP ERROR: {}", hostname, port, e.getMessage(), e);
        return;
      }
      messages.forEach(message -> {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("[{}:{}] UDP > \n{}", hostname, port, message);
        }
          byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
          DatagramPacket packet = new DatagramPacket(bytes, bytes.length, target);
        try {
          channel.send(packet);
        } catch (IOException e) {
          LOGGER.warn("[{}:{}] UDP ERROR: {}", hostname, port, e.getMessage(), e);
        }
      });
    }
  }

  private InetSocketAddress getTarget() throws UnknownHostException {
    if (cachedTarget == null) {
      cachedTarget = new InetSocketAddress(InetAddress.getByName(hostname), port);
    }
    return cachedTarget;
  }

}
