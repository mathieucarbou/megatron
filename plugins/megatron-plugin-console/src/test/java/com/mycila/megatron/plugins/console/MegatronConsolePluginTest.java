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
package com.mycila.megatron.plugins.console;

import com.mycila.megatron.DefaultMegatronConfiguration;
import com.mycila.megatron.test.MegatronTestApi;
import com.mycila.megatron.test.Replay;
import org.assertj.core.util.URLs;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import static com.mycila.megatron.test.Replay.Option.FASTEST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mathieu Carbou
 */
public class MegatronConsolePluginTest {

  @Test
  public void test() throws Exception {
    StringWriter sw = new StringWriter();
    try (MegatronTestApi api = new MegatronTestApi(); MegatronConsolePlugin plugin = new MegatronConsolePlugin(new PrintWriter(sw))) {
      plugin.setApi(api);
      plugin.init(new DefaultMegatronConfiguration().setProperty("megatron.console.enable", "true"));

      Replay replay = new Replay(getClass().getResource("/com/mycila/megatron/test/server.bin"), plugin);
      Future<Void> end = replay.start(FASTEST);
      end.get();

      assertThat(sw.toString()).isEqualTo(URLs.contentOf(getClass().getResource("/out.txt"), StandardCharsets.UTF_8));
    }
  }

}
