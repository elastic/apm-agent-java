---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/plugin-api.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Plugin API [plugin-api]

The plugin API of the Elastic APM Java agent lets you add custom instrumentation to the agent, which the agent will automatically apply the same way as it applies the internally defined instrumentation.

The plugin API is the OpenTelemetry API, plus a dependency to the Plugin SDK (apm-agent-plugin-sdk), and requires a version 1.31.0+ agent.

```xml
<dependency>
    <groupId>co.elastic.apm</groupId>
    <artifactId>apm-agent-plugin-sdk</artifactId>
    <version>${elastic-apm.version}</version>
</dependency>
```

```groovy
compile "co.elastic.apm:apm-agent-plugin-sdk:$elasticApmVersion"
```

Replace the version placeholders with the [latest version from maven central](https://mvnrepository.com/artifact/co.elastic.apm/apm-agent-api/latest): ![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-agent-api.svg "")

An [example repo](https://github.com/elastic/apm-agent-java-plugin-example) and an [article](https://www.elastic.co/blog/create-your-own-instrumentation-with-the-java-agent-plugin) provide a detailed example of adding custom instrumentation for an application to the agent. An overview is

1. subclass `co.elastic.apm.agent.sdk.ElasticApmInstrumentation`
2. specify matchers the define which classes and methods will be instrumented
3. add an instrumentation advice implementation
4. create a `META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation` file which lists the fully qualified instrumentation class names, one class name per line
5. create a plugin jar that includes the instrumentation classes, the `co.elastic.apm.agent.sdk.ElasticApmInstrumentation` file, and any dependencies (apart from the agent itself, though including the plugin API as shown above)
6. start your application with the agent as normal, but additionally with the [plugins_dir configuration option](/reference/config-core.md#config-plugins-dir) set to a directory which includes the created plugin jar (and which should only hold plugin jars, as the agent will attempt to load any jar in that directory).


## Community Plugins [community]

To help our community, we’ve provided a [page](/reference/community-plugins.md) where you can list plugins you create that you think the community can use.

