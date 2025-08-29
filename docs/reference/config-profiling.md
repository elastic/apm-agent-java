---
navigation_title: "Profiling"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-profiling.html
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

# Profiling configuration options [config-profiling]



## `universal_profiling_integration_enabled` [config-universal-profiling-integration-enabled]

```{applies_to}
apm_agent_java: ga 1.50.0
```

If enabled, the apm agent will correlate it's transaction with the profiling data from elastic universal profiling running on the same host.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.universal_profiling_integration_enabled` | `universal_profiling_integration_enabled` | `ELASTIC_APM_UNIVERSAL_PROFILING_INTEGRATION_ENABLED` |


## `universal_profiling_integration_buffer_size` [config-universal-profiling-integration-buffer-size]

```{applies_to}
apm_agent_java: ga 1.50.0
```

The feature needs to buffer ended local-root spans for a short duration to ensure that all of its profiling data has been received. This configuration option configures the buffer size in number of spans. The higher the number of local root spans per second, the higher this buffer size should be set. The agent will log a warning if it is not capable of buffering a span due to insufficient buffer size. This will cause the span to be exported immediately instead with possibly incomplete profiling correlation data.

| Default | Type | Dynamic |
| --- | --- | --- |
| `4096` | Integer | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.universal_profiling_integration_buffer_size` | `universal_profiling_integration_buffer_size` | `ELASTIC_APM_UNIVERSAL_PROFILING_INTEGRATION_BUFFER_SIZE` |


## `universal_profiling_integration_socket_dir` [config-universal-profiling-integration-socket-dir]

```{applies_to}
apm_agent_java: ga 1.50.0
```

The extension needs to bind a socket to a file for communicating with the universal profiling host agent. This configuration option can be used to change the location. Note that the total path name (including the socket) must not exceed 100 characters due to OS restrictions. If unset, the value of the `java.io.tmpdir` system property will be used.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.universal_profiling_integration_socket_dir` | `universal_profiling_integration_socket_dir` | `ELASTIC_APM_UNIVERSAL_PROFILING_INTEGRATION_SOCKET_DIR` |


## `profiling_inferred_spans_enabled` (experimental) [config-profiling-inferred-spans-enabled]

```{applies_to}
apm_agent_java: ga 1.15.0
```

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::


Set to `true` to make the agent create spans for method executions based on [async-profiler](https://github.com/jvm-profiling-tools/async-profiler), a sampling aka statistical profiler.

Due to the nature of how sampling profilers work, the duration of the inferred spans are not exact, but only estimations. The [`profiling_inferred_spans_sampling_interval`](#config-profiling-inferred-spans-sampling-interval) lets you fine tune the trade-off between accuracy and overhead.

The inferred spans are created after a profiling session has ended. This means there is a delay between the regular and the inferred spans being visible in the UI.

Only platform threads are supported. Virtual threads are not supported and will not be profiled.

::::{note}
This feature is not available on Windows and on OpenJ9. In addition only Java 7 to Java 23 are supported.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_enabled` | `profiling_inferred_spans_enabled` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_ENABLED` |


## `profiling_inferred_spans_logging_enabled` [config-profiling-inferred-spans-logging-enabled]

```{applies_to}
apm_agent_java: ga 1.37.0
```

By default, async profiler prints warning messages about missing JVM symbols to standard output. Set this option to `false` to suppress such messages

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_logging_enabled` | `profiling_inferred_spans_logging_enabled` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_LOGGING_ENABLED` |


## `profiling_inferred_spans_sampling_interval` [config-profiling-inferred-spans-sampling-interval]

```{applies_to}
apm_agent_java: ga 1.15.0
```

The frequency at which stack traces are gathered within a profiling session. The lower you set it, the more accurate the durations will be. This comes at the expense of higher overhead and more spans for potentially irrelevant operations. The minimal duration of a profiling-inferred span is the same as the value of this setting.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `50ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `50ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_sampling_interval` | `profiling_inferred_spans_sampling_interval` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_SAMPLING_INTERVAL` |


## `profiling_inferred_spans_min_duration` [config-profiling-inferred-spans-min-duration]

```{applies_to}
apm_agent_java: ga 1.15.0
```

The minimum duration of an inferred span. Note that the min duration is also implicitly set by the sampling interval. However, increasing the sampling interval also decreases the accuracy of the duration of inferred spans.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `0ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_min_duration` | `profiling_inferred_spans_min_duration` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_MIN_DURATION` |


## `profiling_inferred_spans_included_classes` [config-profiling-inferred-spans-included-classes]

```{applies_to}
apm_agent_java: ga 1.15.0
```

If set, the agent will only create inferred spans for methods which match this list. Setting a value may slightly reduce overhead and can reduce clutter by only creating spans for the classes you are interested in. Example: `org.example.myapp.*`

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `*` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_included_classes` | `profiling_inferred_spans_included_classes` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_INCLUDED_CLASSES` |


## `profiling_inferred_spans_excluded_classes` [config-profiling-inferred-spans-excluded-classes]

```{applies_to}
apm_agent_java: ga 1.15.0
```

Excludes classes for which no profiler-inferred spans should be created.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `(?-i)java.*, (?-i)javax.*, (?-i)sun.*, (?-i)com.sun.*, (?-i)jdk.*, (?-i)org.apache.tomcat.*, (?-i)org.apache.catalina.*, (?-i)org.apache.coyote.*, (?-i)org.jboss.as.*, (?-i)org.glassfish.*, (?-i)org.eclipse.jetty.*, (?-i)com.ibm.websphere.*, (?-i)io.undertow.*` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_excluded_classes` | `profiling_inferred_spans_excluded_classes` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_EXCLUDED_CLASSES` |


## `profiling_inferred_spans_lib_directory` [config-profiling-inferred-spans-lib-directory]

```{applies_to}
apm_agent_java: ga 1.18.0
```

Profiling requires that the [async-profiler](https://github.com/jvm-profiling-tools/async-profiler) shared library is exported to a temporary location and loaded by the JVM. The partition backing this location must be executable, however in some server-hardened environments, `noexec` may be set on the standard `/tmp` partition, leading to `java.lang.UnsatisfiedLinkError` errors. Set this property to an alternative directory (e.g. `/var/tmp`) to resolve this. If unset, the value of the `java.io.tmpdir` system property will be used.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.profiling_inferred_spans_lib_directory` | `profiling_inferred_spans_lib_directory` | `ELASTIC_APM_PROFILING_INFERRED_SPANS_LIB_DIRECTORY` |

