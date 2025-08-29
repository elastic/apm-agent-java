---
navigation_title: "Stacktrace"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-stacktrace.html
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

# Stacktrace configuration options [config-stacktrace]



## `application_packages` [config-application-packages]

Used to determine whether a stack trace frame is an *in-app frame* or a *library frame*. This allows the APM app to collapse the stack frames of library code, and highlight the stack frames that originate from your application. Multiple root packages can be set as a comma-separated list; there’s no need to configure sub-packages. Because this setting helps determine which classes to scan on startup, setting this option can also improve startup time.

You must set this option in order to use the API annotations `@CaptureTransaction` and `@CaptureSpan`.

**Example**

Most Java projects have a root package, e.g. `com.myproject`. You can set the application package using Java system properties: `-Delastic.apm.application_packages=com.myproject`

If you are only interested in specific subpackages, you can separate them with commas: `-Delastic.apm.application_packages=com.myproject.api,com.myproject.impl`

::::{note}
the instrumentation aspect of this configuration option - specifying which classes to scan - only applies at startup of the agent and changing the value later won’t affect which classesgot scanned. The UI aspect, showing where stack frames can be collapsed, can be changed at any time.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Collection | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.application_packages` | `application_packages` | `ELASTIC_APM_APPLICATION_PACKAGES` |


## `stack_trace_limit` (performance) [config-stack-trace-limit]

Setting it to 0 will disable stack trace collection. Any positive integer value will be used as the maximum number of frames to collect. Setting it -1 means that all frames will be collected.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `50` | Integer | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stack_trace_limit` | `stack_trace_limit` | `ELASTIC_APM_STACK_TRACE_LIMIT` |


## `span_stack_trace_min_duration` (performance) [config-span-stack-trace-min-duration]

While this is very helpful to find the exact place in your code that causes the span, collecting this stack trace does have some overhead. When setting this option to value `0ms`, stack traces will be collected for all spans. Setting it to a positive value, e.g. `5ms`, will limit stack trace collection to spans with durations equal to or longer than the given value, e.g. 5 milliseconds.

To disable stack trace collection for spans completely, set the value to `-1ms`.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `5ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `5ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.span_stack_trace_min_duration` | `span_stack_trace_min_duration` | `ELASTIC_APM_SPAN_STACK_TRACE_MIN_DURATION` |
