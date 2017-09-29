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
package com.mycila.megatron.server.tests;

import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.server.config.MegatronConfigurationParser;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author cdennis
 */
public class MegatronConfigurationParserTest {

  private MegatronConfigurationParser parser;
  private DocumentBuilderFactory domBuilderFactory;

  @Before
  public void setUp() throws Exception {
    parser = new MegatronConfigurationParser();
    assertThat(parser.getNamespace(), equalTo(URI.create("http://www.mycila.com/config/megatron-config")));

    Collection<Source> schemaSources = new ArrayList<>();
    schemaSources.add(new StreamSource(getClass().getResourceAsStream("/terracotta.xsd")));
    schemaSources.add(parser.getXmlSchema());

    domBuilderFactory = DocumentBuilderFactory.newInstance();
    domBuilderFactory.setNamespaceAware(true);
    domBuilderFactory.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaSources.toArray(new Source[schemaSources.size()])));
  }

  @Test
  public void testValidParse() throws Exception {
    Document dom = domBuilderFactory.newDocumentBuilder().parse(getClass().getResourceAsStream("/valid.xml"));

    MegatronConfiguration config = parser.parse(dom.getDocumentElement(), "what is this thing?");
    assertThat(config.getStatisticCollectorInterval(), equalTo(1_000L));
    assertThat(config.getProperty("port"), equalTo("1234"));
    assertThat(config.getProperty("udpPort"), equalTo("5678"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidUnit() throws Exception {
    Document dom = domBuilderFactory.newDocumentBuilder().parse(getClass().getResourceAsStream("/invalid-unit.xml"));
    parser.parse(dom.getDocumentElement(), "what is this thing?");
  }

}
