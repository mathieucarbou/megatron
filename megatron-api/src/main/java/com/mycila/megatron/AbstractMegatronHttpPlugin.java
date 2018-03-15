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
package com.mycila.megatron;

import com.mycila.megatron.http.BlockingHttpClient;
import com.mycila.megatron.http.NonBlockingHttpClient;
import com.tc.classloader.CommonComponent;

import java.net.URL;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public abstract class AbstractMegatronHttpPlugin extends AbstractMegatronPlugin {

  @Config(required = true) protected URL url;
  @Config protected String prefix = "megatron";
  @Config protected int queueSize = Integer.MAX_VALUE;
  @Config protected boolean async = true;
  @Config protected String[] tags = {};

  protected Client client;

  @Override
  public void enable(MegatronConfiguration configuration) {
    client = async ?
        new NonBlockingHttpClient(url, queueSize <= 0 ? Integer.MAX_VALUE : queueSize, getApi().getThreadFactory()) :
        new BlockingHttpClient(url);
  }

  @Override
  public void close() {
    if (enable) {
      client.close();
    }
  }

}
