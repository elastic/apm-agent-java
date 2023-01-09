<#-- @ftlvariable name="config" type="java.util.Map<java.lang.String,java.util.List<org.stagemonitor.configuration.ConfigurationOption<?>>>" -->
<#-- @ftlvariable name="keys" type="java.util.Collection<java.lang.String>" -->
[[configuration]]
== Configuration

To adapt the Elastic APM Java agent to your needs,
you can configure it using one of the methods below, listed in descending order of precedence:

[horizontal]
1) {apm-app-ref}/agent-configuration.html[Central configuration]::
Configure the Agent in the Kibana APM app.
<<configuration-dynamic, image:./images/dynamic-config.svg[] >>

2) Properties file::
The `elasticapm.properties` file is located in the same folder as the agent jar,
or provided through the <<config-config-file,`config_file`>> option.
image:./images/dynamic-config.svg[link=configuration.html#configuration-dynamic]

3) Java system properties::
All configuration keys are prefixed with `elastic.apm.` +
image:./images/dynamic-config.svg[link=configuration.html#configuration-dynamic]

4) Environment variables::
All configuration keys are in uppercase and prefixed with `ELASTIC_APM_`.

5) Runtime attach parameters::
. `--config` parameter. +
See <<setup-attach-cli>>.
. Arguments of `ElasticApmAttacher.attach(...)`. +
See <<setup-attach-api>>.
. `elasticapm.properties` in classpath root with `ElasticApmAttacher.attach()`. +
See <<setup-attach-api>>.

6) Default values::
Defined for each configuration.

[float]
[[configuration-dynamic]]
=== Dynamic configuration image:./images/dynamic-config.svg[]

Configuration options marked with Dynamic true can be changed at runtime when set from supported sources:

- {apm-app-ref}/agent-configuration.html[Central configuration]
- `elasticapm.properties` file
- Java system properties, but only when set from within the application

NOTE: There are two distinct ways to use `elasticapm.properties`: as an external configuration file, and as a classpath resource. +
Only the external file can be used for dynamic configuration.

[float]
[[configuration-minimal]]
=== Minimal configuration

In order to get started with Elastic APM,
the most important configuration options are <<config-service-name>>,
<<config-server-url>> and <<config-application-packages>>.
Note that even these settings are optional.
Click on their name to see how the default values are determined.

An example configuration looks like this:

[source,bash]
.System properties
----
-Delastic.apm.service_name=my-cool-service
-Delastic.apm.application_packages=org.example,org.another.example
-Delastic.apm.server_url=http://127.0.0.1:8200
----

[source,properties]
.elasticapm.properties
----
service_name=my-cool-service
application_packages=org.example,org.another.example
server_url=http://127.0.0.1:8200
----

[source,bash]
.Environment variables
----
ELASTIC_APM_SERVICE_NAME=my-cool-service
ELASTIC_APM_APPLICATION_PACKAGES=org.example,org.another.example
ELASTIC_APM_SERVER_URL=http://127.0.0.1:8200
----
<#assign defaultServiceName>
Auto-detected based on the rules described above
</#assign>

[float]
=== Option reference

This is a list of all configuration options grouped by their category.
Click on a key to get more information.

<#list config as category, options>
* <<config-${category?lower_case?replace(" ", "-")}>>
    <#list options as option>
** <<config-${option.key?replace("[^a-z]", "-", "r")}>>
    </#list>
</#list>

<#list config as category, options>
[[config-${category?lower_case?replace(" ", "-")}]]
=== ${category} configuration options

++++
<titleabbrev>${category}</titleabbrev>
++++

    <#list options as option>
// This file is auto generated. Please make your changes in *Configuration.java (for example CoreConfiguration.java) and execute ConfigurationExporter
[float]
[[config-${option.key?replace("[^a-z]", "-", "r")}]]
==== `${option.key}`${option.tags?has_content?then(" (${option.tags?join(' ')})", '')}

<#if option.tags?seq_contains("experimental")>
NOTE: This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.

</#if>
${option.description}

<#if option.dynamic><<configuration-dynamic, image:./images/dynamic-config.svg[] >></#if>

<#if option.valueType?matches("TimeDuration")>
  <#if option.valueConverter.canUseMicros>
Supports the duration suffixes `us`, `ms`, `s` and `m`.
  <#else>
Supports the duration suffixes `ms`, `s` and `m`.
  </#if>
Example: `${option.defaultValueAsString}`.
</#if>
<#if option.validOptions?has_content>
Valid options: <#list option.validOptionsLabelMap?values as validOption>`${validOption}`<#if validOption_has_next>, </#if></#list>
</#if>

[options="header"]
|============
| Default                          | Type                | Dynamic
| <#if option.key?matches("service_name")>${defaultServiceName}<#else>`<@defaultValue option/>`</#if> | ${option.valueType} | ${option.dynamic?c}
|============


[options="header"]
|============
| Java System Properties      | Property file   | Environment
| `elastic.apm.${option.key}` | `${option.key}` | `ELASTIC_APM_${option.key?upper_case?replace(".", "_")}`
|============

    </#list>
</#list>

<#macro defaultValue option>${option.defaultValueAsString?has_content?then("${option.defaultValueAsString?replace(',([^\\\\s])', ', $1', 'r')}", '<none>')}</#macro>

[[config-reference-properties-file]]
=== Property file reference

[source,properties]
.elasticapm.properties
----
<#list config as category, options>
############################################
# ${category?right_pad(40)} #
############################################

    <#list options as option>
        <#if !option.tags?seq_contains("internal")>
<#if option.label?has_content>
# ${option.label}
#
</#if>
# ${option.description?replace("\n", "\n# ", "r")}
#
<#if option.validOptions?has_content>
# Valid options: <#list option.validOptionsLabelMap?values as validOption>${validOption}<#if validOption_has_next>, </#if></#list>
</#if>
# ${option.dynamic?then("This setting can be changed at runtime",
    "This setting can not be changed at runtime. Changes require a restart of the application.")}
# Type: ${option.valueType?matches("List|Collection")?then("comma separated list", option.valueType)}
<#if option.valueType?matches("TimeDuration")>
  <#if option.valueConverter.canUseMicros>
# Supports the duration suffixes us, ms, s and m. Example: ${option.defaultValueAsString}.
  <#else>
# Supports the duration suffixes ms, s and m. Example: ${option.defaultValueAsString}.
  </#if>
</#if>
# Default value: ${option.key?matches("service_name")?then(defaultServiceName?replace("\n", "\n# ", "r"), option.defaultValueAsString!)}
#
# ${option.key}=${option.key?matches("service_name")?then('', option.defaultValueAsString!)}

        </#if>
    </#list>
</#list>
----
