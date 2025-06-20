---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/setup-attach-api.html
---

# Programmatic API setup to self-attach [setup-attach-api]

The `apm-agent-attach` setup allows to attach the agent through code, it has the following properties

* A one-line code modification in the application is required to start the agent.
* Does not require to change JVM options.
* The agent is managed as a maven or gradle dependency. This ensures that you have control over which agent version is used for each of your applications.
* Allows programmatic configuration of the agent.


## Supported environments [setup-attach-api-supported-environments]

The attachment is supported on Windows, Unix and Solaris operating systems on HotSpot-based JVMs (like OpenJDK and Oracle JDK) and OpenJ9.


## Caveats [setup-attach-api-caveats]

There can only be one agent instance with one configuration per JVM. So if you deploy multiple web applications to the same application server and call `ElasticApmAttacher.attach()` in each application, the first `attach()` wins and the second one will be ignored. That also means that if you are configuring the agent with `elasticapm.properties`, the application which attaches first gets to decide the configuration. See the default value description of the [`service_name`](/reference/config-core.md#config-service-name) configuration option for ways to have different `service.name`s for each deployment.

The `apm-agent-attach` artifact has a transitive dependency on JNA which can be excluded most of the time when running the application with a JDK. However, it will be required in the following cases:

* When attaching to a JRE and no other copy of JNA is present on the application classpath
* When attaching to a JDK fails and using JRE attach strategy as fallback.


## Usage [setup-attach-api-usage]

Declare a dependency to the [`apm-agent-attach`](https://mvnrepository.com/artifact/co.elastic.apm/apm-agent-attach/latest) artifact.

```xml
<dependency>
    <groupId>co.elastic.apm</groupId>
    <artifactId>apm-agent-attach</artifactId>
    <version>${elastic-apm.version}</version>
</dependency>
```

```groovy
compile "co.elastic.apm:apm-agent-attach:$elasticApmVersion"
```

Call `ElasticApmAttacher.attach()` in the first line of your `public static void main(String[] args)` method.

This example demonstrates the the usage of the `attach` API with a simple Spring Boot application:

```java
import co.elastic.apm.attach.ElasticApmAttacher;
import org.springframework.boot.SpringApplication;

@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        ElasticApmAttacher.attach();
        SpringApplication.run(MyApplication.class, args);
    }
}
```

::::{note}
The API is not limited to Spring Boot and does not require Spring Boot, it is just used for demonstration purposes.
::::



## Configuration [setup-attach-api-configuration]

The recommended way of configuring the agent when using the attach API is to add the configuration to `src/main/resources/elasticapm.properties`.

