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
package com.mycila.megatron.ehcache;

import org.ehcache.xml.XmlConfiguration;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Mathieu Carbou
 */
public class MegatronServiceConfigurationParserTest {

  @Test
  public void xml_config() {
    MegatronServiceConfiguration megatronServiceConfiguration = new XmlConfiguration(getClass().getResource("/ehcache-config.xml"))
        .getServiceCreationConfigurations()
        .stream()
        .filter(MegatronServiceConfiguration.class::isInstance)
        .findFirst()
        .map(MegatronServiceConfiguration.class::cast)
        .get();
    assertThat(megatronServiceConfiguration.getStatisticCollectorInterval(), equalTo(5_000L));
    assertThat(megatronServiceConfiguration.getProperties().size(), equalTo(10));
  }

}
