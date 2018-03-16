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

import org.ehcache.management.registry.ManagementRegistryServiceConfigurationParser;
import org.ehcache.spi.service.ServiceCreationConfiguration;
import org.ehcache.xml.CacheManagerServiceConfigurationParser;
import org.ehcache.xml.XmlModel;
import org.ehcache.xml.exceptions.XmlConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.SIZED;

/**
 * @author Mathieu Carbou
 */
public class MegatronServiceConfigurationParser implements CacheManagerServiceConfigurationParser<MegatronService> {

  private static final String NAMESPACE = "https://mathieucarbou.github.io/megatron/ehcache/v1";
  private static final URI NAMESPACE_URI = URI.create(NAMESPACE);
  private static final URL XML_SCHEMA = ManagementRegistryServiceConfigurationParser.class.getResource("/com/mycila/megatron/ehcache/megatron-ehcache.xsd");

  @Override
  public Source getXmlSchema() throws IOException {
    return new StreamSource(XML_SCHEMA.openStream());
  }

  @Override
  public URI getNamespace() {
    return NAMESPACE_URI;
  }

  @Override
  public ServiceCreationConfiguration<MegatronService> parseServiceCreationConfiguration(Element fragment) {
    if ("megatron".equals(fragment.getLocalName())) {
      DefaultMegatronServiceConfiguration configuration = new DefaultMegatronServiceConfiguration();
      children(fragment, NAMESPACE, "statisticCollectorInterval").findFirst().ifPresent(statisticCollectorInterval -> {
        Duration interval = Duration.of(
            parseLong(val(statisticCollectorInterval, String.valueOf(configuration.getStatisticCollectorInterval()))),
            unit(statisticCollectorInterval, ChronoUnit.MILLIS));
        configuration.setStatisticCollectorInterval(interval.toMillis(), TimeUnit.MILLISECONDS);
      });
      children(fragment, NAMESPACE, "properties")
          .findFirst()
          .ifPresent(properties -> children(properties, NAMESPACE, "set")
              .forEach(property -> configuration.setProperty(
                  attr(property, "name"),
                  attr(property, "value"))));
      return configuration;
    } else {
      throw new XmlConfigurationException(String.format(
          "XML configuration element <%s> in <%s> is not supported",
          fragment.getTagName(), (fragment.getParentNode() == null ? "null" : fragment.getParentNode().getLocalName())));
    }
  }

  private static String attr(Element element, String name) {
    String s = element.getAttribute(name);
    return s == null || s.equals("") ? null : s;
  }

  private static String val(Element element, String def) {
    return element.hasChildNodes() ? element.getFirstChild().getNodeValue() : def;
  }

  private static TemporalUnit unit(Element element, TemporalUnit def) {
    String s = attr(element, "unit");
    return s == null ? def : XmlModel.convertToJavaTimeUnit(org.ehcache.xml.model.TimeUnit.fromValue(s));
  }

  private static Stream<Element> children(Element root, String ns, String localName) {
    NodeList nodeList = root.getElementsByTagNameNS(ns, localName);
    if (nodeList == null || nodeList.getLength() == 0) {
      return Stream.empty();
    }
    return StreamSupport.stream(new Spliterators.AbstractSpliterator<Element>(nodeList.getLength(), DISTINCT | NONNULL | SIZED | IMMUTABLE) {
      int i = 0;

      @Override
      public boolean tryAdvance(Consumer<? super Element> action) {
        Node node;
        if (i >= nodeList.getLength() || (node = nodeList.item(i++)) == null) {
          return false;
        }
        action.accept((Element) node);
        return true;
      }
    }, false);
  }
}
