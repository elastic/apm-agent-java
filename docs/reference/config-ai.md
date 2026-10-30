---
navigation_title: "AI"
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# AI configuration options [config-ai]

## `capture_ai_content` (experimental, security, performance) [config-capture-ai-content]

```{applies_to}
apm_agent_java: ga 1.58.0
```

Enables capture of Spring AI prompt and response messages, embedding inputs, vector store documents and queries, and tool arguments and results.

Captured content can contain sensitive information and can significantly increase heap usage, network traffic, and storage requirements. Review your data handling requirements before enabling this option. Individual captured content values are truncated according to [`long_field_max_length`](/reference/config-core.md#config-long-field-max-length).

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_ai_content` | `capture_ai_content` | `ELASTIC_APM_CAPTURE_AI_CONTENT` |
