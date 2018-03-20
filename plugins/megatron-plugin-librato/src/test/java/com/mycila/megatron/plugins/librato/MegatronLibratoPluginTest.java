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
package com.mycila.megatron.plugins.librato;

import com.mycila.megatron.AbstractMegatronUdpPlugin;
import com.mycila.megatron.DefaultMegatronConfiguration;
import com.mycila.megatron.test.MegatronTestApi;
import com.mycila.megatron.test.Replay;
import com.mycila.megatron.test.UdpServer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import static com.mycila.megatron.test.Replay.Option.FASTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.URLs.contentOf;

/**
 * @author Mathieu Carbou
 */
public class MegatronLibratoPluginTest {

  @Test
  public void test() throws Exception {
    DefaultMegatronConfiguration configuration = new DefaultMegatronConfiguration().loadProperties(getClass().getResource("/megatron.properties"));

    try (UdpServer udpServer = new UdpServer(Integer.parseInt(configuration.getProperty("megatron.librato.port")))) {
      try (MegatronTestApi api = new MegatronTestApi(); AbstractMegatronUdpPlugin plugin = new MegatronLibratoPlugin()) {

        plugin.setApi(api);
        plugin.init(configuration);

        Replay replay = new Replay(getClass().getResource("/com/mycila/megatron/test/server.bin"), plugin);
        Future<Void> end = replay.start(FASTER);
        end.get();

        assertThat(udpServer.getReceivedTest()).isEqualTo(contentOf(getClass().getResource("/out.txt"), StandardCharsets.UTF_8));
      }
    }
  }

}