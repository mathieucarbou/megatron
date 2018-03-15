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
package com.mycila.megatron.server.config;

import com.mycila.megatron.DefaultMegatronConfiguration;
import com.mycila.megatron.MegatronConfiguration;
import com.mycila.megatron.server.config.model.MegatronConfigType;
import com.mycila.megatron.server.config.model.SetType;
import com.mycila.megatron.server.config.model.TimeType;
import org.terracotta.config.TCConfigurationParser;
import org.terracotta.config.service.ExtendedConfigParser;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MegatronConfigurationParser implements ExtendedConfigParser {

  private static final URL XML_SCHEMA = MegatronConfigurationParser.class.getResource("/schemas/megatron-config.xsd");
  private static final URI NAMESPACE = URI.create("http://www.mycila.com/config/megatron-config");

  @Override
  public Source getXmlSchema() throws IOException {
    return new StreamSource(XML_SCHEMA.openStream());
  }

  @Override
  public URI getNamespace() {
    return NAMESPACE;
  }

  @Override
  public MegatronConfiguration parse(Element elmnt, String string) {
    try {
      JAXBContext jaxbContext = JAXBContext.newInstance(MegatronConfigType.class.getPackage().getName(), MegatronConfigurationParser.class.getClassLoader());
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      Collection<Source> schemaSources = new ArrayList<>();
      schemaSources.add(new StreamSource(TCConfigurationParser.TERRACOTTA_XML_SCHEMA.openStream()));
      schemaSources.add(getXmlSchema());
      unmarshaller.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaSources.toArray(new Source[schemaSources.size()])));
      @SuppressWarnings("unchecked")
      JAXBElement<MegatronConfigType> parsed = (JAXBElement<MegatronConfigType>) unmarshaller.unmarshal(elmnt);

      DefaultMegatronConfiguration configuration = new DefaultMegatronConfiguration();
      MegatronConfigType configType = parsed.getValue();

      TimeType statisticCollectorInterval = configType.getStatisticCollectorInterval();
      if (statisticCollectorInterval != null) {
        TimeUnit unit = convertToJavaTimeUnit(statisticCollectorInterval.getUnit());
        configuration.setStatisticCollectorInterval(statisticCollectorInterval.getValue().longValue(), unit);
      }

      List<SetType> props = configType.getSet();
      if (props != null) {
        props.forEach(prop -> configuration.setProperty(prop.getName(), prop.getValue()));
      }

      return configuration;
    } catch (JAXBException e) {
      throw new IllegalArgumentException(e);
    } catch (SAXException | IOException e) {
      throw new AssertionError(e);
    }
  }

  private static TimeUnit convertToJavaTimeUnit(com.mycila.megatron.server.config.model.TimeUnit unit) {
    switch (unit) {
      case NANOS:
        return TimeUnit.NANOSECONDS;
      case MICROS:
        return TimeUnit.MICROSECONDS;
      case MILLIS:
        return TimeUnit.MILLISECONDS;
      case SECONDS:
        return TimeUnit.SECONDS;
      case MINUTES:
        return TimeUnit.MINUTES;
      case HOURS:
        return TimeUnit.HOURS;
      case DAYS:
        return TimeUnit.DAYS;
      default:
        // impossible to go there because XSD is validated first
        throw new AssertionError(unit);
    }
  }

}
