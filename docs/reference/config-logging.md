---
navigation_title: "Logging"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-logging.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Logging configuration options [config-logging]



## `log_level` [config-log-level]

Sets the logging level for the agent. This option is case-insensitive.

::::{note}
`CRITICAL` is a valid option, but it is mapped to `ERROR`; `WARN` and `WARNING` are equivalent; `OFF` is only available since version 1.16.0
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Valid options: `OFF`, `ERROR`, `CRITICAL`, `WARN`, `WARNING`, `INFO`, `DEBUG`, `TRACE`

| Default | Type | Dynamic |
| --- | --- | --- |
| `INFO` | LogLevel | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_level` | `log_level` | `ELASTIC_APM_LOG_LEVEL` |


## `log_file` [config-log-file]

Sets the path of the agent logs. The special value `_AGENT_HOME_` is a placeholder for the folder the elastic-apm-agent.jar is in. Example: `_AGENT_HOME_/logs/elastic-apm.log`

When set to the special value *System.out*, the logs are sent to standard out.

::::{note}
When logging to a file, the log will be formatted in new-line-delimited JSON. When logging to std out, the log will be formatted as plain-text.
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| `System.out` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_file` | `log_file` | `ELASTIC_APM_LOG_FILE` |


## `log_ecs_reformatting` (experimental) [config-log-ecs-reformatting]

```{applies_to}
apm_agent_java: ga 1.22.0
```

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::


Specifying whether and how the agent should automatically reformat application logs into [ECS-compatible JSON](ecs-logging://reference/intro.md), suitable for ingestion into Elasticsearch for further Log analysis. This functionality is available for log4j1, log4j2, Logback and `java.util.logging`. The ECS log lines will include active trace/transaction/error IDs, if there are such.

This option only applies to pattern layouts/formatters by default. See also [`log_ecs_formatter_allow_list`](#config-log-ecs-formatter-allow-list). To properly ingest and parse ECS JSON logs, follow the [getting started guide](ecs-logging-java://reference/setup.md#setup-step-2).

Available options:

* OFF - application logs are not reformatted.
* SHADE - agent logs are reformatted and "shade" ECS-JSON-formatted logs are automatically created in addition to the original application logs. Shade logs will have the same name as the original logs, but with the ".ecs.json" extension instead of the original extension. Destination directory for the shade logs can be configured through the [`log_ecs_reformatting_dir`](#config-log-ecs-reformatting-dir) configuration. Shade logs do not inherit file-rollover strategy from the original logs. Instead, they use their own size-based rollover strategy according to the [`log_file_size`](#config-log-file-size) configuration and while allowing maximum of two shade log files.
* REPLACE - similar to `SHADE`, but the original logs will not be written. This option is useful if you wish to maintain similar logging-related overhead, but write logs to a different location and/or with a different file extension.
* OVERRIDE - same log output is used, but in ECS-compatible JSON format instead of the original format.

::::{note}
while `SHADE` and `REPLACE` options are only relevant to file log appenders, the `OVERRIDE` option is also valid for other appenders, like System out and console.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Valid options: `OFF`, `SHADE`, `REPLACE`, `OVERRIDE`

| Default | Type | Dynamic |
| --- | --- | --- |
| `OFF` | LogEcsReformatting | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_ecs_reformatting` | `log_ecs_reformatting` | `ELASTIC_APM_LOG_ECS_REFORMATTING` |


## `log_ecs_reformatting_additional_fields` [config-log-ecs-reformatting-additional-fields]

```{applies_to}
apm_agent_java: ga 1.26.0
```

A comma-separated list of key-value pairs that will be added as additional fields to all log events. Takes the format `key=value[,key=value[,...]]`, for example: `key1=value1,key2=value2`. Only relevant if [`log_ecs_reformatting`](#config-log-ecs-reformatting) is set to any option other than `OFF`. Additional fields are currently not supported for direct log sending through the agent.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Map | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_ecs_reformatting_additional_fields` | `log_ecs_reformatting_additional_fields` | `ELASTIC_APM_LOG_ECS_REFORMATTING_ADDITIONAL_FIELDS` |


## `log_ecs_formatter_allow_list` [config-log-ecs-formatter-allow-list]

Only formatters that match an item on this list will be automatically reformatted to ECS when [`log_ecs_reformatting`](#config-log-ecs-reformatting) is set to any option other than `OFF`. A formatter is the logging-framework-specific entity that is responsible for the formatting of log events. For example, in log4j it would be a `Layout` implementation, whereas in Logback it would be an `Encoder` implementation.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

| Default | Type | Dynamic |
| --- | --- | --- |
| `*PatternLayout*, org.apache.log4j.SimpleLayout, ch.qos.logback.core.encoder.EchoEncoder, java.util.logging.SimpleFormatter, org.apache.juli.OneLineFormatter, org.springframework.boot.logging.java.SimpleFormatter` | List | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_ecs_formatter_allow_list` | `log_ecs_formatter_allow_list` | `ELASTIC_APM_LOG_ECS_FORMATTER_ALLOW_LIST` |


## `log_ecs_reformatting_dir` [config-log-ecs-reformatting-dir]

If [`log_ecs_reformatting`](#config-log-ecs-reformatting) is set to `SHADE` or `REPLACE`, the shade log files will be written alongside the original logs in the same directory by default. Use this configuration in order to write the shade logs into an alternative destination. Omitting this config or setting it to an empty string will restore the default behavior. If relative path is used, this path will be used relative to the original logs directory.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_ecs_reformatting_dir` | `log_ecs_reformatting_dir` | `ELASTIC_APM_LOG_ECS_REFORMATTING_DIR` |


## `log_file_size` [config-log-file-size]

```{applies_to}
apm_agent_java: ga 1.17.0
```

The size of the log file.

The agent always keeps one history file so that the max total log file size is twice the value of this setting.

| Default | Type | Dynamic |
| --- | --- | --- |
| `50mb` | ByteValue | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_file_size` | `log_file_size` | `ELASTIC_APM_LOG_FILE_SIZE` |


## `log_format_sout` [config-log-format-sout]

```{applies_to}
apm_agent_java: ga 1.17.0
```

Defines the log format when logging to `System.out`.

When set to `JSON`, the agent will format the logs in an [ECS-compliant JSON format](https://github.com/elastic/ecs-logging-java) where each log event is serialized as a single line.

Valid options: `PLAIN_TEXT`, `JSON`

| Default | Type | Dynamic |
| --- | --- | --- |
| `PLAIN_TEXT` | LogFormat | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_format_sout` | `log_format_sout` | `ELASTIC_APM_LOG_FORMAT_SOUT` |


## `log_format_file` [config-log-format-file]

```{applies_to}
apm_agent_java: ga 1.17.0
```

Defines the log format when logging to a file.

When set to `JSON`, the agent will format the logs in an [ECS-compliant JSON format](https://github.com/elastic/ecs-logging-java) where each log event is serialized as a single line.

Valid options: `PLAIN_TEXT`, `JSON`

| Default | Type | Dynamic |
| --- | --- | --- |
| `PLAIN_TEXT` | LogFormat | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_format_file` | `log_format_file` | `ELASTIC_APM_LOG_FORMAT_FILE` |


## `log_sending` (experimental) [config-log-sending]

```{applies_to}
apm_agent_java: ga 1.36.0
```

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::


Sends agent and application logs directly to APM Server.

Note that logs can get lost if the agent can’t keep up with the logs, if APM Server is not available, or if Elasticsearch can’t index the logs fast enough.

For better delivery guarantees, it’s recommended to ship ECS JSON log files with Filebeat See also [`log_ecs_reformatting`](#config-log-ecs-reformatting). Log sending does not currently support custom MDC fields, `log_ecs_reformatting` and shipping the logs with Filebeat must be used if custom MDC fields are required.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.log_sending` | `log_sending` | `ELASTIC_APM_LOG_SENDING` |

