---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/setup-javaagent.html
---

# Manual setup with -javaagent flag [setup-javaagent]

Using the `-javaagent` option is the most common way to set up java agents on a JVM, it has the following properties:

* No application code changes required.
* Requires to change JVM arguments, which implies a restart of the whole JVM.
* For application servers, the JVM arguments modification requires changing application server configuration
* Agent artifact is an extra binary to manage alongside the JVM or application server.
* Ensures that the application is fully instrumented before it starts.


### Get the Java agent [setup-javaagent-get-agent]

The first step in getting started with the Elastic APM Java agent is to retrieve a copy of the agent jar.

:::::::{tab-set}

::::::{tab-item} Maven Central
Java agent releases are published to [Maven central](https://repo.maven.apache.org/maven2/), in order to get a copy you can either:

* download manually the [latest agent](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=co.elastic.apm&a=elastic-apm-agent&v=LATEST) or [previous releases](https://mvnrepository.com/artifact/co.elastic.apm/elastic-apm-agent) from Maven central.
* download with `curl`:

    ```bash
    curl -o 'elastic-apm-agent.jar' -L 'https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=co.elastic.apm&a=elastic-apm-agent&v=LATEST'
    ```


In addition, we also publish a dedicated build of the Java agent which ships the latest log4j2 version and for that reason requires at least Java 8. This build can also be found on [Maven central](https://mvnrepository.com/artifact/co.elastic.apm/elastic-apm-agent-java8/latest).
::::::

::::::{tab-item} Docker
Java agent releases are published as Docker images  through the `docker.elastic.co` registry.

The `latest` tag allows to use the most recent release at the time the image is built.

Adding the following statement in `Dockerfile` will copy the agent jar to `/elastic-apm-agent.jar`.

```
COPY --from=docker.elastic.co/observability/apm-agent-java:latest /usr/agent/elastic-apm-agent.jar /elastic-apm-agent.jar
```
::::::

:::::::
::::{note}
Agents are not like regular application dependencies. Don’t declare a dependency to the agent in your application.
::::



### Add `-javaagent` flag [setup-javaagent-add-flag]

When starting your application, add the JVM flag `-javaagent:/path/to/elastic-apm-agent-<version>.jar`


## Set up the agent with Application Servers [application-server-setup]

Different application servers have different ways of setting the `-javaagent` flag and system properties.

Note that system properties are only one way of configuring the agent but setting the `-javaagent` flag is required in each case. See [*Configuration*](/reference/configuration.md) to learn about how to configure the agent with a configuration file or environment variables.


### Generic Setup [setup-generic]

Start your application (for example a Spring Boot application or other embedded servers) and add the `-javaagent` JVM flag. Use the `-D` prefix to configure the agent using system properties.

```bash
java -javaagent:/path/to/elastic-apm-agent-<version>.jar -Delastic.apm.service_name=my-cool-service -Delastic.apm.application_packages=org.example,org.another.example -Delastic.apm.server_url=http://127.0.0.1:8200 -jar my-application.jar
```


### Apache Tomcat [setup-tomcat]


#### Unix [setup-tomcat-unix]

Create `bin/setenv.sh` (or modify if the file already exists). Make sure to make the file executable, for example `chmod +x bin/setenv.sh`

Add the following line:

```bash
export CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/elastic-apm-agent-<version>.jar"
export CATALINA_OPTS="$CATALINA_OPTS -Delastic.apm.service_name=my-cool-service"
export CATALINA_OPTS="$CATALINA_OPTS -Delastic.apm.application_packages=org.example,org.another.example"
export CATALINA_OPTS="$CATALINA_OPTS -Delastic.apm.server_url=http://127.0.0.1:8200"
```


#### Windows [setup-tomcat-windows]

Create `bin\setenv.bat` (or modify if the file already exists).

```text
set CATALINA_OPTS=%CATALINA_OPTS% -javaagent:C:/path/to/elastic-apm-agent-<version>.jar
set CATALINA_OPTS=%CATALINA_OPTS% -Delastic.apm.service_name=my-cool-service
set CATALINA_OPTS=%CATALINA_OPTS% -Delastic.apm.application_packages=org.example,org.another.example
set CATALINA_OPTS=%CATALINA_OPTS% -Delastic.apm.server_url=http://127.0.0.1:8200
```


### Jetty [setup-jetty]

Option 1: edit `jetty.sh`

```bash
export JAVA_OPTIONS="${JAVA_OPTIONS} -javaagent:/path/to/elastic-apm-agent-<version>.jar"
export JAVA_OPTIONS="${JAVA_OPTIONS} -Delastic.apm.service_name=my-cool-service"
export JAVA_OPTIONS="${JAVA_OPTIONS} -Delastic.apm.application_packages=org.example,org.another.example"
export JAVA_OPTIONS="${JAVA_OPTIONS} -Delastic.apm.server_url=http://127.0.0.1:8200"
```

Option 2: edit `start.ini`

```ini
--exec
-javaagent:/path/to/elastic-apm-agent-<version>.jar
-Delastic.apm.service_name=my-cool-service
-Delastic.apm.application_packages=org.example,org.another.example
-Delastic.apm.server_url=http://127.0.0.1:8200
```

Option 3: If you are using embedded Jetty, see [Generic Setup](#setup-generic).


### JBoss EAP/WildFly [setup-jboss-wildfly]


#### Standalone Mode [setup-jboss-wildfly-standalone]

Add the agent configuration at the bottom of the `standalone.conf` file:

**Unix**

```bash
export JAVA_OPTS="$JAVA_OPTS -javaagent:/path/to/elastic-apm-agent-<version>.jar"
export JAVA_OPTS="$JAVA_OPTS -Delastic.apm.service_name=my-cool-service"
export JAVA_OPTS="$JAVA_OPTS -Delastic.apm.application_packages=org.example,org.another.example"
export JAVA_OPTS="$JAVA_OPTS -Delastic.apm.server_url=http://127.0.0.1:8200"
```

**Windows**

```bash
set JAVA_OPTS=%JAVA_OPTS% -javaagent:C:/path/to/elastic-apm-agent-<version>.jar
set JAVA_OPTS=%JAVA_OPTS% -Delastic.apm.service_name=my-cool-service
set JAVA_OPTS=%JAVA_OPTS% -Delastic.apm.application_packages=org.example,org.another.example
set JAVA_OPTS=%JAVA_OPTS% -Delastic.apm.server_url=http://127.0.0.1:8200
```


#### Domain Mode [setup-jboss-wildfly-domain]

Edit the `domain.xml` file which is usually located under `domain/configuration` and add a JVM option for the `-javaagent` flag, as well as some system properties for the configuration.

```xml
...
<server-group>
  <jvm>
     <jvm-options>
      ...
      <option value="-javaagent:/path/to/elastic-apm-agent-<version>.jar"/>
      ...
     </jvm-options>
  </jvm>
</server-group>
...
<system-properties>
  <property name="elastic.apm.service_name"         value="my-cool-service"/>
  <property name="elastic.apm.application_packages" value="org.example,org.another.example"/>
  <property name="elastic.apm.server_url"          value="http://127.0.0.1:8200"/>
</system-properties>
...
```


### WebSphere Liberty [setup-websphere-liberty]

Add the following lines to the `jvm.options` file.

```text
-javaagent:/path/to/elastic-apm-agent-<version>.jar
-Delastic.apm.service_name=my-cool-service
-Delastic.apm.application_packages=org.example,org.another.example
-Delastic.apm.server_url=http://127.0.0.1:8200
```


### Payara [setup-payara]

Update the `domain.xml` file to add the `-javaagent` flag and system properties.

```xml
<java-config>
  ...
  <jvm-options>-javaagent:/path/to/elastic-apm-agent-<version>.jar</jvm-options>
  <jvm-options>-Delastic.apm.service_name=my-cool-service</jvm-options>
  <jvm-options>-Delastic.apm.application_packages=org.example,org.another.example</jvm-options>
  <jvm-options>-Delastic.apm.server_url=http://127.0.0.1:8200</jvm-options>
</java-config>
```


### Oracle WebLogic [setup-weblogic]


#### Unix [setup-weblogic-unix]

Edit the `startWebLogic.sh` file and add the following lines after the `setDomainEnv.sh` call:

```bash
export JAVA_OPTIONS="$JAVA_OPTIONS -javaagent:/path/to/elastic-apm-agent-<version>.jar"
export JAVA_OPTIONS="$JAVA_OPTIONS -Delastic.apm.service_name=my-cool-service"
export JAVA_OPTIONS="$JAVA_OPTIONS -Delastic.apm.application_packages=org.example,org.another.example"
export JAVA_OPTIONS="$JAVA_OPTIONS -Delastic.apm.server_url=http://127.0.0.1:8200"
```


#### Windows [setup-weblogic-windows]

Edit the `startWebLogic.cmd` file and add the following lines after the `setDomainEnv.cmd` call:

```text
set JAVA_OPTIONS=%JAVA_OPTIONS% -javaagent:C:/path/to/elastic-apm-agent-<version>.jar
set JAVA_OPTIONS=%JAVA_OPTIONS% -Delastic.apm.service_name=my-cool-service
set JAVA_OPTIONS=%JAVA_OPTIONS% -Delastic.apm.application_packages=org.example,org.another.example
set JAVA_OPTIONS=%JAVA_OPTIONS% -Delastic.apm.server_url=http://127.0.0.1:8200
```


### Cloud Foundry [setup-cloud-foundry]

The Elastic Java APM Agent Framework is now part of the Cloud Foundry Java Buildpack as of [Release v4.19](https://github.com/cloudfoundry/java-buildpack/releases/tag/v4.19).

A user provided Elastic APM service must have a name or tag with `elastic-apm` in it so that the Elastic APM Agent Framework will automatically configure the application to work with the service.

Create a user provided service:

`cf cups my-elastic-apm-service -p '{"server_url":"my-apm-server-url","secret_token":"my-apm-server-secret-token"}'`

Both `my-apm-server-url` and `my-apm-server-secret-token` are respectively `server_url` and `secret_token` from service-key of your Elasticsearch server.

Bind the application to the service:

`cf bind-service my-application my-elastic-apm-service`

and restage the application or use the `services` block in the application manifest file.

For more details on the Elastic Java APM Agent Framework for Cloud Foundry see [here](https://github.com/cloudfoundry/java-buildpack/blob/main/docs/framework-elastic_apm_agent.md).

```yaml
applications:
- name: my-application
  memory: 1G
  path: ./target/my-application.jar
  services:
    - my-elastic-apm-service
```
