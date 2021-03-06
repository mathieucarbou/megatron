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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommonComponent
public final class BlockingHttpClient implements Client {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlockingHttpClient.class);

  private final URL url;
  private volatile boolean closed;

  public BlockingHttpClient(URL url) {
    this.url = url;
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      LOGGER.info("Closing...");
    }
  }

  @Override
  public void send(Stream<String> messages) {
    if (!closed) {
      Http.post(url, messages.collect(Collectors.joining("\n")) + "\n");
    }
  }

}
