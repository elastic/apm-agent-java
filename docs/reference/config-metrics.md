---
navigation_title: "Metrics"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-metrics.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Metrics configuration options [config-metrics]



## `dedot_custom_metrics` [config-dedot-custom-metrics]

```{applies_to}
apm_agent_java: ga 1.22.0
```

Replaces dots with underscores in the metric names for Micrometer metrics.

::::{warning}
Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch. An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`. The first metric maps `foo` to a number and the second metric maps `foo` as an object.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.dedot_custom_metrics` | `dedot_custom_metrics` | `ELASTIC_APM_DEDOT_CUSTOM_METRICS` |


## `custom_metrics_histogram_boundaries` (experimental) [config-custom-metrics-histogram-boundaries]

```{applies_to}
apm_agent_java: ga 1.37.0
```

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::


Defines the default bucket boundaries to use for OpenTelemetry histograms.

Note that for OpenTelemetry 1.32.0 or newer this setting will only work when using API only. The default buckets will not be applied when bringing your own SDK.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0.00390625, 0.00552427, 0.0078125, 0.0110485, 0.015625, 0.0220971, 0.03125, 0.0441942, 0.0625, 0.0883883, 0.125, 0.176777, 0.25, 0.353553, 0.5, 0.707107, 1.0, 1.41421, 2.0, 2.82843, 4.0, 5.65685, 8.0, 11.3137, 16.0, 22.6274, 32.0, 45.2548, 64.0, 90.5097, 128.0, 181.019, 256.0, 362.039, 512.0, 724.077, 1024.0, 1448.15, 2048.0, 2896.31, 4096.0, 5792.62, 8192.0, 11585.2, 16384.0, 23170.5, 32768.0, 46341.0, 65536.0, 92681.9, 131072.0` | List | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.custom_metrics_histogram_boundaries` | `custom_metrics_histogram_boundaries` | `ELASTIC_APM_CUSTOM_METRICS_HISTOGRAM_BOUNDARIES` |


## `metric_set_limit` [config-metric-set-limit]

```{applies_to}
apm_agent_java: ga 1.33.0
```

Limits the number of active metric sets. The metrics sets have associated labels, and the metrics sets are held internally in a map using the labels as keys. The map is limited in size by this option to prevent unbounded growth. If you reach the limit, you'll receive a warning in the agent log. The recommended option to workaround the limit is to try to limit the cardinality of the labels, eg naming your transactions so that there are fewer distinct transaction names. But if you must, you can use this option to increase the limit.

| Default | Type | Dynamic |
| --- | --- | --- |
| `1000` | Integer | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.metric_set_limit` | `metric_set_limit` | `ELASTIC_APM_METRIC_SET_LIMIT` |


## `agent_reporter_health_metrics` [config-agent-reporter-health-metrics]

```{applies_to}
apm_agent_java: ga 1.35.0
```

Enables metrics which capture the health state of the agent's event reporting mechanism.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.agent_reporter_health_metrics` | `agent_reporter_health_metrics` | `ELASTIC_APM_AGENT_REPORTER_HEALTH_METRICS` |


## `agent_background_overhead_metrics` [config-agent-background-overhead-metrics]

```{applies_to}
apm_agent_java: ga 1.35.0
```

Enables metrics which capture the resource consumption of agent background tasks.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.agent_background_overhead_metrics` | `agent_background_overhead_metrics` | `ELASTIC_APM_AGENT_BACKGROUND_OVERHEAD_METRICS` |

