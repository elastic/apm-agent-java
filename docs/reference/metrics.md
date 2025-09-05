---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/metrics.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Metrics [metrics]

The Java agent tracks certain system and application metrics. Some of them have built-in visualizations and some can only be visualized with custom Kibana dashboards.

These metrics will be sent regularly to the APM Server and from there to Elasticsearch. You can adjust the interval with the setting [`metrics_interval`](/reference/config-reporter.md#config-metrics-interval).

The metrics will be stored in the `apm-*` index and have the `processor.event` property set to `metric`.

::::{note}
Dedicated JVM metrics views are available since Elastic stack version 7.2. Starting in 7.5, metrics are aggregated separately for each JVM, relying on the ID of the underlying system — either container ID (where applicable) or hostname. Starting in Java agent version 1.11.0, it is possible to manually configure a unique name for each service node/JVM through [`service_node_name`](/reference/config-core.md#config-service-node-name). When multiple JVMs are running on the same host and report data for the same service, this configuration is required in order to be able to view metrics at the JVM level.
::::


* [System metrics](#metrics-system)
* [cgroup metrics](#metrics-cgroup)
* [JVM Metrics](#metrics-jvm)
* [JMX metrics](#metrics-jmx)
* [Built-in application metrics](#metrics-application)
* [Use the agent for metrics collection only](#metrics-only-mode)
* [OpenTelemetry metrics](#metrics-otel)
* [Micrometer metrics](#metrics-micrometer)
* [Agent Health Metrics](#metrics-agenthealth)


## System metrics [metrics-system]

Host metrics. As of version 6.6, these metrics will be visualized in the APM app.

For more system metrics, consider installing [metricbeat](beats://reference/metricbeat/index.md) on your hosts.

**`system.cpu.total.norm.pct`**
:   type: scaled_float

format: percent

The percentage of CPU time in states other than Idle and IOWait, normalised by the number of cores.


**`system.process.cpu.total.norm.pct`**
:   type: scaled_float

format: percent

The percentage of CPU time spent by the process since the last event. This value is normalized by the number of CPU cores and it ranges from 0 to 100%.


**`system.memory.total`**
:   type: long

format: bytes

Total memory.


**`system.memory.actual.free`**
:   type: long

format: bytes

Actual free memory in bytes. It is calculated based on the OS. On Linux it consists of the free memory plus caches and buffers. On OSX it is a sum of free memory and the inactive memory. On Windows, this value does not include memory consumed by system caches and buffers.


**`system.process.memory.size`**
:   type: long

format: bytes

The total virtual memory the process has.



## cgroup metrics (added in 1.18.0) [metrics-cgroup]

Linux’s cgroup metrics.

**`system.process.cgroup.memory.mem.limit.bytes`**
:   type: long

format: bytes

Memory limit for current cgroup slice.


**`system.process.cgroup.memory.mem.usage.bytes`**
:   type: long

format: bytes

Memory usage in current cgroup slice.



## JVM Metrics [metrics-jvm]

JVM-specific metrics

**`jvm.memory.heap.used`**
:   type: long

format: bytes

The amount of used heap memory in bytes


**`jvm.memory.heap.committed`**
:   type: long

format: bytes

The amount of heap memory in bytes that is committed for the Java virtual machine to use. This amount of memory is guaranteed for the Java virtual machine to use.


**`jvm.memory.heap.max`**
:   type: long

format: bytes

The maximum amount of heap memory in bytes that can be used for memory management. If the maximum memory size is undefined, the value is `-1`.


**`jvm.memory.heap.pool.used`**
:   type: long

format: bytes

The amount of used memory in bytes of the heap memory pool specified by the `name` label

labels

* name: The name representing this memory pool


**`jvm.memory.heap.pool.committed`**
:   type: long

format: bytes

The amount of memory in bytes that is committed for the heap memory pool specified by the `name` label. This amount of memory is guaranteed for this specific pool.

labels

* name: The name representing this memory pool


**`jvm.memory.heap.pool.max`**
:   type: long

format: bytes

The maximum amount of memory in bytes that can be used for the heap memory pool specified by the `name` label.

labels

* name: The name representing this memory pool


**`jvm.memory.non_heap.used`**
:   type: long

format: bytes

The amount of used non-heap memory in bytes


**`jvm.memory.non_heap.committed`**
:   type: long

format: bytes

The amount of non-heap memory in bytes that is committed for the Java virtual machine to use. This amount of memory is guaranteed for the Java virtual machine to use.


**`jvm.memory.non_heap.max`**
:   type: long

format: bytes

The maximum amount of non-heap memory in bytes that can be used for memory management. If the maximum memory size is undefined, the value is `-1`.


**`jvm.memory.non_heap.pool.used`**
:   type: long

format: bytes

The amount of used memory in bytes of the non-heap memory pool specified by the `name` label

labels

* name: The name representing this memory pool


**`jvm.memory.non_heap.pool.committed`**
:   type: long

format: bytes

The amount of memory in bytes that is committed for the non-heap memory pool specified by the `name` label. This amount of memory is guaranteed for this specific pool.

labels

* name: The name representing this memory pool


**`jvm.memory.non_heap.pool.max`**
:   type: long

format: bytes

The maximum amount of memory in bytes that can be used for the non-heap memory pool specified by the `name` label.

labels

* name: The name representing this memory pool


**`jvm.thread.count`**
:   type: int

The current number of live threads in the JVM, including both daemon and non-daemon threads.


**`jvm.gc.count`**
:   type: long

labels

* name: The name representing this memory manager (for example `G1 Young Generation`, `G1 Old Generation`)

The total number of collections that have occurred.


**`jvm.gc.time`**
:   type: long

format: ms

labels

* name: The name representing this memory manager (for example `G1 Young Generation`, `G1 Old Generation`)

The approximate accumulated collection elapsed time in milliseconds.


**`jvm.gc.alloc`**
:   type: long

format: bytes

An approximation of the total amount of memory, in bytes, allocated in heap memory.


**`jvm.fd.used`**
:   type: long

The current number of opened file descriptors.


**`jvm.fd.max`**
:   type: long

The maximum number of opened file descriptors.



## JMX metrics [metrics-jmx]

Java Management Extensions (JMX) provides a common management interface on the JVM and is often used to expose internal metrics through this interface.

The Elastic APM agent is able to connect to the JMX interface directly without needing additional credentials or changing JVM parameters unlike other external tools like VisualVM or Jconsole. Also, it only captures metrics and does not expose the whole JMX management interface to the end-user.

JMX metrics to capture need to be configured through the [`capture_jmx_metrics`](/reference/config-jmx.md#config-capture-jmx-metrics) option.


## Built-in application metrics [metrics-application]

To power the [Time spent by span type](docs-content://solutions/observability/apm/transactions-ui.md) graph, the agent collects summarized metrics about the timings of spans and transactions, broken down by span type.

**`span.self_time`**
:   type: simple timer

This timer tracks the span self-times and is the basis of the transaction breakdown visualization.

Fields:

* `sum.us`: The sum of all span self-times in microseconds since the last report (the delta)
* `count`: The count of all span self-times since the last report (the delta)

You can filter and group by these dimensions:

* `transaction.name`: The name of the transaction
* `transaction.type`: The type of the transaction, for example `request`
* `span.type`: The type of the span, for example `app`, `template` or `db`
* `span.subtype`: The sub-type of the span, for example `mysql` (optional)



## Use the agent for metrics collection only [metrics-only-mode]

There are cases where you would want to use the agent only to collect and ship metrics, without tracing any Java code. In such cases, you may set the [`instrument`](/reference/config-core.md#config-instrument) config option to `false`. By doing so, the agent will minimize its effect on the application, while still collecting and sending metrics to the APM Server.


## OpenTelemetry metrics [metrics-otel]

The elastic APM Java Agent supports collecting metrics defined via OpenTelemetry. See the corresponding [documentation section](/reference/opentelemetry-bridge.md#otel-metrics) for details.


## Micrometer metrics [metrics-micrometer]

::::{warning}
This functionality is in beta and is subject to change. The design and code is less mature than official GA features and is being provided as-is with no warranties. Beta features are not subject to the support SLA of official GA features.
::::


The Elastic APM Java agent lets you use the popular metrics collection framework [Micrometer](https://micrometer.io/) to track custom application metrics.

Some use cases for tracking custom metrics from your application include monitoring performance-related things like cache statistics, thread pools, or page hits. However, you can also track business-related metrics such as revenue and correlate them with performance metrics. Metrics registered to a Micrometer `MeterRegistry` are aggregated in memory and reported every [`metrics_interval`](/reference/config-reporter.md#config-metrics-interval). Based on the metadata about the service and the timestamp, you can correlate metrics with traces. The advantage is that the metrics won’t be affected by the [sampling rate](/reference/config-core.md#config-transaction-sample-rate) and usually take up less space. That is because not every event is stored individually.

The limitation of tracking metrics is that you won’t be able to attribute a value to a specific transaction. If you’d like to do that, [add labels](/reference/public-api.md#api-transaction-add-tag) to your transaction instead of tracking the metric with Micrometer. The tradeoff here is that you either have to do 100% sampling or account for the missing events. The reason for that is that if you set your sampling rate to 10%, for example, you’ll only be storing one out of 10 requests. The labels you set on non-sampled transactions will be lost.

* [Notes](#metrics-micrometer-beta-notes)
* [Get started with existing Micrometer setup](#metrics-micrometer-get-started-existing)
* [Get started from scratch](#metrics-micrometer-get-started-from-scratch)
* [Get started with Spring Boot](#metrics-micrometer-spring-boot)
* [Supported Meters](#metrics-micrometer-fields)


### Notes [metrics-micrometer-beta-notes]

* Dots in metric names of Micrometer metrics get replaced with underscores to avoid mapping conflicts. De-dotting can be disabled via [`dedot_custom_metrics`](/reference/config-metrics.md#config-dedot-custom-metrics).
* Histograms ([DistributionSummary](https://www.javadoc.io/doc/io.micrometer/micrometer-core/latest/io/micrometer/core/instrument/DistributionSummary.md), [Timer](https://www.javadoc.io/doc/io.micrometer/micrometer-core/latest/io/micrometer/core/instrument/Timer.md), and [LongTaskTimer](https://www.javadoc.io/doc/io.micrometer/micrometer-core/latest/io/micrometer/core/instrument/LongTaskTimer.md)) are supported by converting the histogram metric into three derived metrics: a counter of the values, the sum of the values, and the [histogram](elasticsearch://reference/elasticsearch/mapping-reference/histogram.md). For example, `DistributionSummary.builder("order").register(...).record(orderPrice)` will create three metrics: `order.sum`, `order.count` and `order.histogram` (which has a `values` array for the buckets and a `counts` array for counts of samples in each bucket).
* When multiple `MeterRegistry` s are used, the metrics are de-duplicated based on their meter id. A warning is issued if a collision occurs from multiple meter registries within a compound meter registry.
* When using `CountingMode.CUMULATIVE`, you can use TSVB’s "Positive Rate" aggregation to convert the counter to a rate. But you have to remember to group by a combination of dimensions that uniquely identify the time series. This may be a combination of `host.name` and `service.name`, or the `kubernetes.pod.id`.
* Micrometer metrics can be disabled by using the [`disable_instrumentations`](/reference/config-core.md#config-disable-instrumentations) setting.


### Get started with existing Micrometer setup [metrics-micrometer-get-started-existing]

Attach the agent, and you’re done! The agent automatically detects all `MeterRegistry` instances and reports all metrics to APM Server (in addition to where they originally report). When attaching the agent after the application has already started, the agent detects a `MeterRegistry` when calling any public method on it. If you are using multiple registries within a `CompoundMeterRegistry`, the agent only reports the metrics once.


### Verify Micrometer data [verify-micrometer-data]

Use Discover to validate that metrics are successfully reported to Kibana.

1. Launch {{kib}}.
2. Open the main menu, then click **Discover**.
3. Select `apm-*` as your index pattern.
4. Filter the data to only show documents with metrics: `processor.name :"metric"`
5. Optionally, apply additional filters by service or host names if Micrometer was only instrumented on a subset of your environment.

You should now see documents containing both metrics collected by the APM agent and custom metrics from Micrometer. Narrow your search with a known Micrometer metric field. For example, if you know you have registered the metric name `cache.puts` in the Micrometer `MeterRegistry`, add `cache_puts: *` (dots are replaced with underscores) to your search to return only Micrometer metrics documents.


### Visualize Micrometer data [visualize-micrometer-data]

::::{note}
Monotonically increased counters and Positive rate aggregations are not fully supported in the current version.
::::


[TSVB](docs-content://explore-analyze/visualize/legacy-editors/tsvb.md) is the recommended visualization for Micrometer metrics. First, make sure to select the right aggregation. The most common options are:

* Sum: Useful for business metrics
* Average: Usually used for performance-related metrics

It’s common to group metrics by attributes, including Micrometer labels or attributes already collected by APM agents. This could be service versions, runtime versions, or even cloud metadata.

::::{tip}
See the [Event rates and rate of change in TSVB](https://www.elastic.co/blog/visualizing-observability-with-kibana-event-rates-and-rate-of-change-in-tsvb) blog post for more information.
::::



### Get started from scratch [metrics-micrometer-get-started-from-scratch]

Declare a dependency to Micrometer:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>${micrometer.version}</version>
</dependency>
```

Create a Micrometer `MeterRegistry`.

```java
MeterRegistry registry = new SimpleMeterRegistry(new SimpleConfig() {

        @Override
        public CountingMode mode() {
            // to report the delta since the last report
            // this makes building dashbaords a bit easier
            return CountingMode.STEP;
        }

        @Override
        public Duration step() {
            // the duration should match metrics_interval, which defaults to 30s
            return Duration.ofSeconds(30);
        }

        @Override
        public String get(String key) {
            return null;
        }
    }, Clock.SYSTEM);
```


### Get started with Spring Boot [metrics-micrometer-spring-boot]

The easiest way to get started with Spring Boot is to add a dependency to [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.md). Spring Boot Actuator provides dependency management and auto-configuration for Micrometer.

Use the `management.metrics.export.simple` prefix to configure via `application.properties`

```properties
management.metrics.export.simple.enabled=true
management.metrics.export.simple.step=30s
management.metrics.export.simple.mode=STEP
```


### Supported Meters [metrics-micrometer-fields]

This section lists all supported Micrometer `Meter` s and describes how they are mapped to Elasticsearch documents.

Micrometer tags are nested under `labels`. Example:

```json
"labels": {
  "tagKey1": "tagLabel1",
  "tagKey2": "tagLabel2",
}
```

Labels are great for breaking down metrics by different dimensions. Although there is no upper limit, note that a high number of distinct values per label (aka high cardinality) may lead to higher memory usage, higher index sizes, and slower queries. Also, make sure the number of distinct tag keys is limited to avoid [mapping explosions](docs-content://manage-data/data-store/mapping.md#mapping-limit-settings).

Depending on the meter type, some meters might be exported as multiple metrics to elasticsearch. The resulting fields are shown below for each meter type. Note that the [`disable_metrics`](/reference/config-reporter.md#config-disable-metrics) option operates on the original name of the metric, not on the generated fields. For that reason it is only possible to disable all fields of a given metric, not individual ones.

**`Timer`**
:   Fields:

* `${name}.sum.us`: The total time of recorded events (the delta when using `CountingMode.STEP`). This is equivalent to `timer.totalTime(TimeUnit.MICROSECONDS)`.
* `${name}.count`: The number of times that stop has been called on this timer (the delta when using `CountingMode.STEP`). This is equivalent to `timer.count()`.


**`FunctionTimer`**
:   Fields:

* `${name}.sum.us`: The total time of all occurrences of the timed event (the delta when using `CountingMode.STEP`). This is equivalent to `functionTimer.totalTime(TimeUnit.MICROSECONDS)`.
* `${name}.count`: The total number of occurrences of the timed event (the delta when using `CountingMode.STEP`). This is equivalent to `functionTimer.count()`.


**`LongTaskTimer`**
:   Fields:

* `${name}.sum.us`: The cumulative duration of all current tasks (the delta when using `CountingMode.STEP`). This is equivalent to `longTaskTimer.totalTime(TimeUnit.MICROSECONDS)`.
* `${name}.count`: The current number of tasks being executed (the delta when using `CountingMode.STEP`) This is equivalent to `longTaskTimer.activeTasks()`.


**`DistributionSummary`**
:   Fields:

* `${name}.sum`: The total amount of all recorded events (the delta when using `CountingMode.STEP`). This is equivalent to `distributionSummary.totalAmount()`.
* `${name}.count`: The number of times that record has been called (the delta when using `CountingMode.STEP`). This is equivalent to `distributionSummary.count()`.


**`Gauge`**
:   Fields:

* `${name}`: The value of `gauge.value()`.


**`Counter`**
:   Fields:

* `${name}`: The value of `counter.count()` (the delta when using `CountingMode.STEP`).


**`FunctionCounter`**
:   Fields:

* `${name}`: The value of `functionCounter.count()` (the delta when using `CountingMode.STEP`).



## Agent Health Metrics [metrics-agenthealth]

The agent internally uses a queue to buffer the various events (e.g. transactions, spans, metrics) before sending them to the APM server. When [`agent_reporter_health_metrics`](/reference/config-metrics.md#config-agent-reporter-health-metrics) is enabled, the agent will expose several metrics regarding the health state of this queue and the network connectivity to the APM server. In addition, if [`agent_background_overhead_metrics`](/reference/config-metrics.md#config-agent-background-overhead-metrics) is enabled, the agent will continuously measure the resource consumption of its own background tasks and provide the results as metrics.


### Agent Reporting and Event Metrics [metrics-agenthealth-events]

**`agent.events.total`**
:   type: long

format: number of events

The total number of events attempted to report to the APM server.


**`agent.events.dropped`**
:   type: long

format: number of events

The number of events which could not be sent to the APM server, e.g. due to a full queue or an error.


**`agent.events.queue.min_size.pct`**
:   type: double

format: percentage [0-1]

The minimum size of the reporting queue since the last metrics report.


**`agent.events.queue.max_size.pct`**
:   type: double

format: percentage [0-1]

The maximum size of the reporting queue since the last metrics report.


**`agent.events.requests.count`**
:   type: long

format: number of requests

The number of requests made (successful and failed) to the APM server to report data.


**`agent.events.requests.bytes`**
:   type: long

format: bytes

The number of bytes attempted to send (successful and failed) to the APM server to report data.



### Agent Background Resource Consumption Metrics [metrics-agenthealth-overhead]

**`agent.background.cpu.total.pct`**
:   type: double

format: percentage [0-1]

The total CPU usage caused by background tasks running in the agent.


**`agent.background.cpu.overhead.pct`**
:   type: double

format: percentage [0-1]

The share of process CPU usage caused by background tasks running in the agent.


**`agent.background.memory.allocation.bytes`**
:   type: long

format: bytes

The number of bytes allocated in the heap by background tasks running in the agent.


**`agent.background.threads.count`**
:   type: long

format: number of threads

The number of threads used by background tasks in the agent.


