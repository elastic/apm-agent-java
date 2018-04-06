<#-- @ftlvariable name="config" type="java.util.Map<java.lang.String,java.util.List<org.stagemonitor.configuration.ConfigurationOption<?>>>" -->
[configuration]
== Configuration
To adapt the Elastic APM agent to your needs,
you can configure it using different configuration sources,
which have different naming conventions for the property key.
The first configuration sources override the configuration values of over the latter sources.

[arabic]
. Java system properties +
 All configuration keys are prefixed with `elastic.apm.`
. Environment variables +
 All configuration keys are in uppercase and prefixed with `ELASTIC_APM_`
. `elasticapm.properties` file +
 This file has to be placed under `src/main/resources/elasticapm.properties`

Configuration options marked with Dynamic true can be changed at runtime
via configuration sources which support dynamic reloading.
Java system properties can be set from within the application.
The `elasticapm.properties` file will be regularly polled for updates.


<#list config as category, options>
[[${category?lower_case?replace(" ", "-")}]]
=== ${category} configuration options
    <#list options as option>
[float]
[[config-${option.key?replace("[^a-z]", "-", "r")}]]
==== `${option.key}`

${option.description}


[options="header"]
|============
| Default                          | Type                | Dynamic
| `<@defaultValue option/>` | ${option.valueType} | ${option.dynamic?c}
|============


[options="header"]
|============
| Java System Properties      | Environment                            | `elasticapm.properties`
| `elastic.apm.${option.key}` | `ELASTIC_APM_${option.key?upper_case}` | `${option.key}`
|============

    </#list>
</#list>

<#macro defaultValue option>${option.defaultValueAsString?has_content?then("pass:[${option.defaultValueAsString}]",'<none>')}</#macro>

