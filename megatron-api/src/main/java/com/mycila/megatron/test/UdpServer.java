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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * @author Mathieu Carbou
 */
public class UdpServer implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(UdpServer.class);

  private final int port;
  private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
  private final Thread thread;
  private final DatagramSocket serverSocket;

  public UdpServer(int port) {
    this(port, 65507); // theoretical max buffer size on Windows
  }

  public UdpServer(int port, int bufferLenght) {
    this.port = port;

    LOGGER.info("Starting UDP Server on port " + port + "...");
    try {
      serverSocket = new DatagramSocket(port);
    } catch (SocketException e) {
      throw new IllegalArgumentException("Unable to listen for UDP packets on port " + port + ": " + e.getMessage(), e);
    }

    this.thread = new Thread(() -> {
      byte[] buffer = new byte[bufferLenght];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        while (!Thread.currentThread().isInterrupted()) {
          serverSocket.receive(packet);
          baos.write(buffer, 0, packet.getLength());
          packet.setData(buffer); // reset counters
        }
      } catch (IOException e) {
        if (!(e instanceof SocketException && e.getMessage().equals("Socket closed"))) {
          e.printStackTrace();
        }
      }
    });

    this.thread.setDaemon(true);
    this.thread.start();
  }

  public int getPort() {
    return port;
  }

  public byte[] getReceivedBytes() {
    return baos.toByteArray();
  }

  public String getReceivedTest() {
    return new String(getReceivedBytes(), StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    LOGGER.info("Stopping UDP Server on port " + port + "...");
    serverSocket.close();
    thread.interrupt();
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
