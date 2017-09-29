<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Megatron](#megatron)
  - [Installation](#installation)
  - [CONSOLE Plugin](#console-plugin)
    - [Configuration](#configuration)
  - [REST Plugin](#rest-plugin)
    - [Configuration](#configuration-1)
    - [Endpoints](#endpoints)
    - [Screenshots](#screenshots)
  - [Datadog Plugin](#datadog-plugin)
    - [Configuration](#configuration-2)
    - [Screenshots](#screenshots-1)
  - [Librato Plugin](#librato-plugin)
    - [Configuration](#configuration-3)
    - [Screenshots](#screenshots-2)
  - [Graphite Plugin](#graphite-plugin)
    - [Configuration](#configuration-4)
    - [Screenshots](#screenshots-3)
  - [StatsD Plugin](#statsd-plugin)
    - [Configuration](#configuration-5)
    - [Screenshots](#screenshots-4)
  - [Write your own plugin!](#write-your-own-plugin)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Megatron

[![Build Status](https://travis-ci.org/mathieucarbou/megatron.svg?branch=master)](https://travis-ci.org/mathieucarbou/megatron)

Megatron is a Terracotta server plugin that enables to query or stream statistics and notifications from the platform and clients to an external monitoring system like NewRelic, Librato, Datadog, Graphite, any StatsD collector, etc.

![https://mathieucarbou.github.io/megatron/assets/images/architecture.png](https://mathieucarbou.github.io/megatron/assets/images/architecture.png)

## Installation

  1. Download the distribution at [https://github.com/mathieucarbou/megatron/releases](https://github.com/mathieucarbou/megatron/releases)
  2. Unzip the archive
  3. Copy all the jar files from `server/plugins/lib` directory into your Terracotta Ehcache Kit.
  4. Configure your `tc-config.xml` file to add one or several properties to enable plugins. See `server/tc-config-sample.xml` as an example.
  5. Start the servers and look inside your monitoring platform!
  
## CONSOLE Plugin

Just outputs in the server console the received statistics and notifications.

### Configuration

Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.console.enable" value="true"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

## REST Plugin

### Configuration

Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.rest.enable" value="true"/>
          <mc:set name="megatron.rest.bindAddress" value="0.0.0.0"/>
          <mc:set name="megatron.rest.port" value="9470"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

### Endpoints

```bash
# get active server name
curl http://active:9470/api/v1/platform/server
# trigger a server state dump on the server logs
curl http://active:9470/api/v1/platform/dump
# get the server XML config
curl http://active:9470/api/v1/platform/config

# get the stripe live topology
curl http://active:9470/api/v1/topology
# get the live servers
curl http://active:9470/api/v1/topology/servers
# get the live clients
curl http://active:9470/api/v1/topology/clients
# get the live ehcache clients
curl http://active:9470/api/v1/topology/clients/ehcache

# get statistics
curl http://active:9470/api/v1/statistics
curl http://active:9470/api/v1/statistics?statNames=Cache:UpdateCount,Cache:MissCount
curl http://active:9470/api/v1/statistics/clients/{clientId}
curl http://active:9470/api/v1/statistics/clients/{clientId}?statNames=Cache:UpdateCount,Cache:MissCount
curl http://active:9470/api/v1/statistics/clients/{clientId}/cacheManagers/{cmName}
curl http://active:9470/api/v1/statistics/clients/{clientId}/cacheManagers/{cmName}?statNames=Cache:UpdateCount,Cache:MissCount
curl http://active:9470/api/v1/statistics/clients/{clientId}/cacheManagers/{cmName}/caches/{cName}
curl http://active:9470/api/v1/statistics/clients/{clientId}/cacheManagers/{cmName}/caches/{cName}?statNames=Cache:UpdateCount,Cache:MissCount
```

### Screenshots

![https://mathieucarbou.github.io/megatron/assets/images/plugin-rest.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-rest.png)

<a href="http://www.youtube.com/watch?feature=player_embedded&v=B6KE40NcHFU" target="_blank"><img src="http://img.youtube.com/vi/B6KE40NcHFU/0.jpg" width="240" height="180" border="10" /></a>

## Datadog Plugin

The Datadog plugin supports __metrics tagging__ to facilitate aggregation filtering.
The format of the data sent is: `metric.name:value|type|@sample_rate|#tag1:value,tag2`.
See [https://docs.datadoghq.com/guides/dogstatsd/](https://docs.datadoghq.com/guides/dogstatsd/).

### Configuration

1. Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.datadog.enable" value="true"/>
          <mc:set name="megatron.datadog.server" value="localhost"/>
          <mc:set name="megatron.datadog.port" value="8125"/>
          <mc:set name="megatron.datadog.prefix" value="megatron"/>
          <mc:set name="megatron.datadog.tags" value="stripe:stripe1,cluster:MyCluster"/>
          <mc:set name="megatron.datadog.async" value="true"/>
          <mc:set name="megatron.datadog.queueSize" value="-1"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

`megatron.datadog.server` is your Datadog Agent address that is running a StatsD server ([https://docs.datadoghq.com/guides/dogstatsd/](https://docs.datadoghq.com/guides/dogstatsd/)).

You can run one by executing:

```bash
docker run -d --name dd-agent -v /var/run/docker.sock:/var/run/docker.sock:ro -v /proc/:/host/proc/:ro -v /sys/fs/cgroup/:/host/sys/fs/cgroup:ro -e API_KEY=<YOUR_DATADOG_API_KEY> -e SD_BACKEND=docker -p 8125:8125/udp datadog/docker-dd-agent:latest
```

Be careful to set your `YOUR_DATADOG_API_KEY`.

See [https://app.datadoghq.com/account/settings#agent/docker](https://app.datadoghq.com/account/settings#agent/docker) 

### Screenshots

![https://mathieucarbou.github.io/megatron/assets/images/plugin-datadog-1.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-datadog-1.png)
![https://mathieucarbou.github.io/megatron/assets/images/plugin-datadog-2.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-datadog-2.png)

## Librato Plugin

The Librato plugin supports __metrics tagging__ to facilitate aggregation filtering.
Metrics are sent in the Graphite form: `my.metric#service=web,app_version=1.12:42`.
See [https://www.librato.com/docs/kb/collect/collection_agents/stastd/](https://www.librato.com/docs/kb/collect/collection_agents/stastd/).

### Configuration

1. Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.librato.enable" value="true"/>
          <mc:set name="megatron.librato.server" value="localhost"/>
          <mc:set name="megatron.librato.port" value="8125"/>
          <mc:set name="megatron.librato.prefix" value="megatron"/>
          <mc:set name="megatron.librato.tags" value="stripe=stripe1,cluster=MyCluster"/>
          <mc:set name="megatron.librato.async" value="true"/>
          <mc:set name="megatron.librato.queueSize" value="-1"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

`megatron.librato.server` is your Etsy/StatsD server configured to work with Librato backend (see provided configuration sample):

```js
{
  port: 8125,
  keyNameSanitize: false,
  backends: ["statsd-librato-backend"],

  // See https://github.com/librato/statsd-librato-backend
  librato: {
    email: "your_librato_email",
    token: "your_librato_api_key",
    skipInternalMetrics: false
  }
}
```

### Screenshots

![https://mathieucarbou.github.io/megatron/assets/images/plugin-librato-1.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-librato-1.png)
![https://mathieucarbou.github.io/megatron/assets/images/plugin-librato-2.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-librato-2.png)

## Graphite Plugin

The Graphite plugin supports __metrics tagging__ to facilitate aggregation filtering. 
Metrics are sent in the Graphite form: `[prefix.name][;tag1=value1;tag2=value2] [value] [timestamp]`.
See [http://graphite.readthedocs.io/en/latest/feeding-carbon.html](http://graphite.readthedocs.io/en/latest/feeding-carbon.html).

Requires Graphite/Carbon 1.1.x.

### Configuration

Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.graphite.enable" value="true"/>
          <mc:set name="megatron.graphite.server" value="localhost"/>
          <mc:set name="megatron.graphite.port" value="2003"/>
          <mc:set name="megatron.graphite.prefix" value="megatron"/>
          <mc:set name="megatron.graphite.tags" value="stripe=stripe1;cluster=MyCluster"/>
          <mc:set name="megatron.graphite.async" value="true"/>
          <mc:set name="megatron.graphite.queueSize" value="-1"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

`megatron.graphite.server` is your Graphite server

### Screenshots

![https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite-1.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite-1.png)
![https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite-2.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite-2.png)

## StatsD Plugin

The StatsD plugin does not supports metrics tagging. Metrics are sent in the form: `[prefix.name]:[value]|[type]`

### Configuration

Edit your `tc-config.xml` file to add the plugin configurations:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tc-config xmlns="http://www.terracotta.org/config">
  <plugins>
    <config xmlns:mc='http://www.mycila.com/config/megatron-config'>
      <mc:megatron-config>
        <mc:statisticCollectorInterval unit="seconds">5</mc:statisticCollectorInterval>
          <mc:set name="megatron.statsd.enable" value="true"/>
          <mc:set name="megatron.statsd.server" value="localhost"/>
          <mc:set name="megatron.statsd.port" value="8125"/>
          <mc:set name="megatron.statsd.prefix" value="megatron"/>
          <mc:set name="megatron.statsd.async" value="true"/>
          <mc:set name="megatron.statsd.queueSize" value="-1"/>
      </mc:megatron-config>
    </config>
  </plugins>
</tc-config>
```

`megatron.statsd.server` is your Etsy/StatsD server configured to work with any StatsD backend.

List StatsD backends (non exhaustive): [https://github.com/etsy/statsd/blob/master/docs/backend.md](https://github.com/etsy/statsd/blob/master/docs/backend.md)

There are several examples of StatsD backend configurations in the [distribution module](https://github.com/mathieucarbou/megatron/tree/master/megatron-distribution/src/main/dist/statsd)

- __For Graphite__

```js
{
  port: 8125,
  backends: ["./backends/graphite"],
  // graphite support
  graphiteHost: 'localhost',
  graphitePort: 2003,
  globalPrefix: '',
}
```

- __For Ganglia__

```js
{
  port: 8125,
  backends: ['statsd-ganglia-backend'],
  // Ganglia: https://github.com/jbuchbinder/statsd-ganglia-backend
  ganglia: {
    host: 'localhost',
      port: 8649,
  }
}

```

- __For NewRelic__

```js
{
  port: 8125,
  backends: ['statsd-newrelic-backend'],  
  // NewRelic: https://github.com/mpayetta/statsd-newrelic-backend 
  newRelicLicense: "your_newrelic_license_key",
  newRelicApp: "your_newrelic_app_name",
  dispatchers: ['customEvent', 'customMetric'],
}

```

### Screenshots

- With NewRelic:

![https://mathieucarbou.github.io/megatron/assets/images/plugin-newrelic.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-newrelic.png)

- With Graphite (using the StatsD collector)

![https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite.png](https://mathieucarbou.github.io/megatron/assets/images/plugin-graphite.png)

## Write your own plugin!

You can easily write a plugin: the API os really simple. Look at the current implementations in the project. It is as easy as writing a class like this one:

```java
@Namespace("megatron.ganglia")
public class MegatronGangliaPlugin extends AbstractMegatronPlugin {

  @Config private String server = "localhost";
  @Config private int port = 8649;
  @Config private String prefix = "megatron";

  private GangliaClient client;

  @Override
  public void enable(MegatronConfiguration configuration, MegatronApi api) {
    client = new GangliaClient(prefix, server, port);  
  }

  @Override
  public void close() {
    if (enable) {
      client.stop();
    }
  }

  @Override
  public void onNotification(ContextualNotification notification) {
    if (enable) {
      logger.trace("onNotification({})", notification.getType());
      String prefix = buildFullPrefix("events", notification);
      client.count(prefix + notification.getType(), 1);
    }
  }

  @Override
  public void onStatistics(ContextualStatistics statistics) {
    if (enable) {
      logger.trace("onStatistics({})", statistics.size());
      String prefix = buildFullPrefix("statistics", statistics);
      for (Map.Entry<String, Number> entry : statistics.getStatistics().entrySet()) {
        String statName = escape(entry.getKey());
        Number value = entry.getValue();
        if (value instanceof Double || value instanceof Float) {
          client.gauge(prefix + statName, value.doubleValue());
        } else {
          client.gauge(prefix + statName, value.longValue());
        }
      }
    }
  }

}
```
