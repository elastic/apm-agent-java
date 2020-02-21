<#-- @ftlvariable name="options" type="java.util.List<org.stagemonitor.configuration.ConfigurationOption<?>>" -->
<#-- @ftlvariable name="validatorAccessor" type="co.elastic.apm.agent.configuration.ConfigurationExporterTest.ValidatorAccessor" -->
[
    <#list options as option>
    {
        "key": "${option.key}",
        "type": "${option.validOptions?has_content?then("Enum", option.valueType)?json_string}",
        "category": "${option.configurationCategory?json_string}",
        "default": "${option.defaultValueAsString?json_string}",
        <#if option.tags?has_content>
        "tags": [${option.tags?filter(tag -> !tag?starts_with("added"))?map(tag -> '"${tag?json_string}"')?join(", ")}],
        </#if>
        <#if option.validOptions?has_content>
        "enum": [${option.validOptions?map(option -> '"${option?json_string}"')?join(", ")}],
        </#if>
        "since": "${option.tags?filter(tag -> tag?starts_with("added"))?map(added -> added?replace("added\\[(.*?)\\]", "$1", "r"))?first!"1.0.0"}",
        <#assign regexValidator= validatorAccessor.getRegexValidator(option)!/>
        <#assign rangeValidator= validatorAccessor.getRangeValidator(option)!/>
        <#if regexValidator?has_content || rangeValidator?has_content>
        "validation": {
        <#if rangeValidator?has_content>
            <#if rangeValidator.getMin()?has_content>
            "min": ${rangeValidator.getMin()?is_number?then(rangeValidator.getMin(), '"${rangeValidator.getMin()}"')},
            </#if>
            <#if rangeValidator.getMax()?has_content>
            "max": ${rangeValidator.getMax()?is_number?then(rangeValidator.getMax(), '"${rangeValidator.getMax()}"')},
            </#if>
            "negativeMatch": ${rangeValidator.negativeMatch?c}${regexValidator?has_content?then(",", "")}
        </#if>
        <#if regexValidator?has_content>
            "regex": "${regexValidator.pattern}"
        </#if>
        },
        </#if>
        "description": "${option.description?json_string}"
    }${option?has_next?then(",", "")}
    </#list>
]
