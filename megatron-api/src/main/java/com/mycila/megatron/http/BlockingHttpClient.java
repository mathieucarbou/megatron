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

public final class BlockingHttpClient implements Client {

  private final URL url;
  private volatile boolean closed;

  public BlockingHttpClient(String hostname, int port) {
    try {
      this.url = new URL("http://" + hostname + ":" + port + "/metrics/job/megatron");
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(hostname + ":" + port + " invalid: " + e.getMessage(), e);
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
    }
  }

  public void send(List<String> messages) {
    if (!closed) {
      for (String message : messages) {
        Http.send(url, message);
      }
    }
  }

}
