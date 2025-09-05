---
navigation_title: "Huge Traces"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-huge-traces.html
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

# Huge Traces configuration options [config-huge-traces]



## `span_compression_enabled` [config-span-compression-enabled]

```{applies_to}
apm_agent_java: ga 1.30.0
```

Setting this option to true will enable span compression feature. Span compression reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that some information such as DB statements of all the compressed spans will not be collected.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.span_compression_enabled` | `span_compression_enabled` | `ELASTIC_APM_SPAN_COMPRESSION_ENABLED` |


## `span_compression_exact_match_max_duration` [config-span-compression-exact-match-max-duration]

```{applies_to}
apm_agent_java: ga 1.30.0
```

Consecutive spans that are exact match and that are under this threshold will be compressed into a single composite span. This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that the DB statements of all the compressed spans will not be collected.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `50ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `50ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.span_compression_exact_match_max_duration` | `span_compression_exact_match_max_duration` | `ELASTIC_APM_SPAN_COMPRESSION_EXACT_MATCH_MAX_DURATION` |


## `span_compression_same_kind_max_duration` [config-span-compression-same-kind-max-duration]

```{applies_to}
apm_agent_java: ga 1.30.0
```

Consecutive spans to the same destination that are under this threshold will be compressed into a single composite span. This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that the DB statements of all the compressed spans will not be collected.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `0ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.span_compression_same_kind_max_duration` | `span_compression_same_kind_max_duration` | `ELASTIC_APM_SPAN_COMPRESSION_SAME_KIND_MAX_DURATION` |


## `exit_span_min_duration` [config-exit-span-min-duration]

```{applies_to}
apm_agent_java: ga 1.30.0
```

Exit spans are spans that represent a call to an external service, like a database. If such calls are very short, they are usually not relevant and can be ignored.

::::{note}
If a span propagates distributed tracing ids, it will not be ignored, even if it is shorter than the configured threshold. This is to ensure that no broken traces are recorded.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `us`, `ms`, `s` and `m`. Example: `0ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.exit_span_min_duration` | `exit_span_min_duration` | `ELASTIC_APM_EXIT_SPAN_MIN_DURATION` |

