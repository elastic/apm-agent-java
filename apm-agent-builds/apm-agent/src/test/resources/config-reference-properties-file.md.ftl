---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-reference-properties-file.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Property file reference [config-reference-properties-file]

```properties
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
# Default value: ${option.key?matches("service_name")?then('Auto-detected based on the rules described above', option.defaultValueAsString!)}
#
# ${option.key}=${option.key?matches("service_name")?then('', option.defaultValueAsString!)}

        </#if>
    </#list>
</#list>
```
