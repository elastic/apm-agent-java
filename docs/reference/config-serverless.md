---
navigation_title: "Serverless"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-serverless.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
products:
  - id: cloud-serverless
  - id: observability
  - id: apm
---

# Serverless configuration options [config-serverless]



## `aws_lambda_handler` [config-aws-lambda-handler]

```{applies_to}
apm_agent_java: ga 1.28.0
```

This config option must be used when running the agent in an AWS Lambda context. This config value allows to specify the fully qualified name of the class handling the lambda function. An empty value (default value) indicates that the agent is not running within an AWS lambda function.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.aws_lambda_handler` | `aws_lambda_handler` | `ELASTIC_APM_AWS_LAMBDA_HANDLER` |


## `data_flush_timeout` [config-data-flush-timeout]

```{applies_to}
apm_agent_java: ga 1.28.0
```

This config value allows to specify the timeout in milliseconds for flushing APM data at the end of a serverless function. For serverless functions, APM data is written in a synchronous way, thus, blocking the termination of the function util data is written or the specified timeout is reached.

| Default | Type | Dynamic |
| --- | --- | --- |
| `1000` | Long | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.data_flush_timeout` | `data_flush_timeout` | `ELASTIC_APM_DATA_FLUSH_TIMEOUT` |

