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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class NonBlockingHttpClient implements Client {

  private final BlockingQueue<String> messages;
  private final Thread sender;
  private final URL url;

  private volatile boolean closed;

  public NonBlockingHttpClient(String hostname, int port) {
    this(hostname, port, Integer.MAX_VALUE);
  }

  public NonBlockingHttpClient(String hostname, int port, int queueSize) {
    try {
      this.url = new URL("http://" + hostname + ":" + port + "/metrics/job/megatron");
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(hostname + ":" + port + " invalid: " + e.getMessage(), e);
    }

    this.messages = new LinkedBlockingQueue<>(queueSize);

    this.sender = new Thread(() -> {
      while (!closed && !Thread.currentThread().isInterrupted()) {
        try {
          String message = messages.poll(1, TimeUnit.SECONDS);
          if (message != null) {
            internalSend(message);
          }
        } catch (InterruptedException e) {
          tryDequeue();
          closed = true;
        }
      }
    }, NonBlockingHttpClient.class.getSimpleName() + "[" + hostname + ":" + port + "]");

    this.sender.start();
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
    }
  }

  public void send(List<String> messages) {
    if (!closed) {
      messages.forEach(this.messages::offer);
    }
  }

  private void internalSend(String message) {
    Http.send(url, message);
  }

  private void tryDequeue() {
    while (!messages.isEmpty()) {
      internalSend(messages.poll());
    }
  }

}
