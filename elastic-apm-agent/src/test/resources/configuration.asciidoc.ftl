<#-- @ftlvariable name="config" type="java.util.Map<java.lang.String,java.util.List<org.stagemonitor.configuration.ConfigurationOption<?>>>" -->
<#-- @ftlvariable name="keys" type="java.util.Collection<java.lang.String>" -->
[[configuration]]
== Configuration
To adapt the Elastic APM agent to your needs,
you can configure it using different configuration sources,
which have different naming conventions for the property key.
In descending order of precedence:

[arabic]
. {apm-app-ref}/agent-configuration.html[Central configuration]
. Java system properties +
  All configuration keys are prefixed with `elastic.apm.`
. Environment variables +
  All configuration keys are in uppercase and prefixed with `ELASTIC_APM_`
. `elasticapm.properties` file +
  You can place a `elasticapm.properties` in the same directory the agent jar resides in.
  To customize the location, set the <<config-config-file>> option.
  No prefix is required for the configuration keys.

Central configuration overrides all other settings. Java system properties override Environment variables. Environment variables override `elasticapm.properties`

Configuration options marked with Dynamic true can be changed at runtime via configuration sources which support dynamic reloading.
{kibana-ref}/agent-configuration.html[Central configuration] and the `elasticapm.properties` file are such sources.
Java system properties can be dynamic as well by being set from within the application.

In order to get started with Elastic APM,
the most important configuration options are <<config-service-name>>,
<<config-server-urls>> and <<config-application-packages>>.
So a minimal version of a configuration might look like this:

[source,bash]
.System properties
----
-Delastic.apm.service_name=my-cool-service
-Delastic.apm.application_packages=org.example,org.another.example
-Delastic.apm.server_urls=http://localhost:8200
----

[source,properties]
.elasticapm.properties
----
service_name=my-cool-service
application_packages=org.example,org.another.example
server_urls=http://localhost:8200
----

[source,bash]
.Environment variables
----
ELASTIC_APM_SERVICE_NAME=my-cool-service
ELASTIC_APM_APPLICATION_PACKAGES=org.example,org.another.example
ELASTIC_APM_SERVER_URLS=http://localhost:8200
----
<#assign defaultServiceName>
For Spring-based application, uses the `spring.application.name` property, if set.
For Servlet-based applications, uses the `display-name` of the `web.xml`, if available.
Falls back to the servlet context path the application is mapped to (unless mapped to the root context).
Falls back to the name of the main class or jar file.
If the service name is set explicitly, it overrides all of the above.
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
    <#list options as option>
// This file is auto generated. Please make your changes in *Configuration.java (for example CoreConfiguration.java) and execute ConfigurationExporter
[float]
[[config-${option.key?replace("[^a-z]", "-", "r")}]]
==== `${option.key}`${option.tags?has_content?then(" (${option.tags?join(' ')})", '')}

${option.description}

<#if option.valueType?matches("TimeDuration")>
Supports the duration suffixes `ms`, `s` and `m`.
Example: `${option.defaultValueAsString}`.
The default unit for this option is `${option.valueConverter.defaultDurationSuffix}`.
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
# Supports the duration suffixes ms, s and m. Example: ${option.defaultValueAsString}.
# The default unit for this option is ${option.valueConverter.defaultDurationSuffix}.
</#if>
# Default value: ${option.key?matches("service_name")?then(defaultServiceName?replace("\n", "\n# ", "r"), option.defaultValueAsString!)}
#
# ${option.key}=${option.key?matches("service_name")?then('', option.defaultValueAsString!)}

        </#if>
    </#list>
</#list>
----
