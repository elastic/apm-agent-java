---
navigation_title: "Circuit-Breaker"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-circuit-breaker.html
---

# Circuit-Breaker configuration options [config-circuit-breaker]



## `circuit_breaker_enabled` ([1.14.0] performance) [config-circuit-breaker-enabled]

A boolean specifying whether the circuit breaker should be enabled or not. When enabled, the agent periodically polls stress monitors to detect system/process/JVM stress state. If ANY of the monitors detects a stress indication, the agent will become inactive, as if the [`recording`](/reference/config-core.md#config-recording) configuration option has been set to `false`, thus reducing resource consumption to a minimum. When inactive, the agent continues polling the same monitors in order to detect whether the stress state has been relieved. If ALL monitors approve that the system/process/JVM is not under stress anymore, the agent will resume and become fully functional.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.circuit_breaker_enabled` | `circuit_breaker_enabled` | `ELASTIC_APM_CIRCUIT_BREAKER_ENABLED` |


## `stress_monitoring_interval` (performance) [config-stress-monitoring-interval]

The interval at which the agent polls the stress monitors. Must be at least `1s`.

Supports the duration suffixes `ms`, `s` and `m`. Example: `5s`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `5s` | TimeDuration | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitoring_interval` | `stress_monitoring_interval` | `ELASTIC_APM_STRESS_MONITORING_INTERVAL` |


## `stress_monitor_gc_stress_threshold` (performance) [config-stress-monitor-gc-stress-threshold]

The threshold used by the GC monitor to rely on for identifying heap stress. The same threshold will be used for all heap pools, so that if ANY has a usage percentage that crosses it, the agent will consider it as a heap stress. The GC monitor relies only on memory consumption measured after a recent GC.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `0.95` | Double | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitor_gc_stress_threshold` | `stress_monitor_gc_stress_threshold` | `ELASTIC_APM_STRESS_MONITOR_GC_STRESS_THRESHOLD` |


## `stress_monitor_gc_relief_threshold` (performance) [config-stress-monitor-gc-relief-threshold]

The threshold used by the GC monitor to rely on for identifying when the heap is not under stress . If `stress_monitor_gc_stress_threshold` has been crossed, the agent will consider it a heap-stress state. In order to determine that the stress state is over, percentage of occupied memory in ALL heap pools should be lower than this threshold. The GC monitor relies only on memory consumption measured after a recent GC.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `0.75` | Double | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitor_gc_relief_threshold` | `stress_monitor_gc_relief_threshold` | `ELASTIC_APM_STRESS_MONITOR_GC_RELIEF_THRESHOLD` |


## `stress_monitor_cpu_duration_threshold` (performance) [config-stress-monitor-cpu-duration-threshold]

The minimal time required in order to determine whether the system is either currently under stress, or that the stress detected previously has been relieved. All measurements during this time must be consistent in comparison to the relevant threshold in order to detect a change of stress state. Must be at least `1m`.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `1m`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `1m` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitor_cpu_duration_threshold` | `stress_monitor_cpu_duration_threshold` | `ELASTIC_APM_STRESS_MONITOR_CPU_DURATION_THRESHOLD` |


## `stress_monitor_system_cpu_stress_threshold` (performance) [config-stress-monitor-system-cpu-stress-threshold]

The threshold used by the system CPU monitor to detect system CPU stress. If the system CPU crosses this threshold for a duration of at least `stress_monitor_cpu_duration_threshold`, the monitor considers this as a stress state.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `0.95` | Double | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitor_system_cpu_stress_threshold` | `stress_monitor_system_cpu_stress_threshold` | `ELASTIC_APM_STRESS_MONITOR_SYSTEM_CPU_STRESS_THRESHOLD` |


## `stress_monitor_system_cpu_relief_threshold` (performance) [config-stress-monitor-system-cpu-relief-threshold]

The threshold used by the system CPU monitor to determine that the system is not under CPU stress. If the monitor detected a CPU stress, the measured system CPU needs to be below this threshold for a duration of at least `stress_monitor_cpu_duration_threshold` in order for the monitor to decide that the CPU stress has been relieved.

[![dynamic config](/reference/images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `0.8` | Double | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.stress_monitor_system_cpu_relief_threshold` | `stress_monitor_system_cpu_relief_threshold` | `ELASTIC_APM_STRESS_MONITOR_SYSTEM_CPU_RELIEF_THRESHOLD` |

