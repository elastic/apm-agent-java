---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/method-sampling-based.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Sampling-based profiler [method-sampling-based]

::::{warning}
This functionality is in technical preview and may be changed or removed in a future release. Elastic will work to fix any issues, but features in technical preview are not subject to the support SLA of official GA features.
::::


::::{note}
this feature is not supported on Windows and on OpenJ9
::::


Instead of recording every event, leverage the agent’s built-in integration with [async-profiler](https://github.com/jvm-profiling-tools/async-profiler) to periodically request the stack trace from all actively running threads. This means measurements do not need to be inserted into all methods, which keeps the overhead of this approach extremely low. Stack traces are then correlated with span activation events, and profiler-inferred spans for slow methods are created. Just like that, we’ve detected exactly what is executing between current transactions and spans.


## Use cases [_use_cases]

* **Development**: When trying to find out why the request you just made was slow.
* **Load testing / Production**: When analyzing why some requests are slower than others.
* **Customer support**: When a user complains that a particular request they made at noon was slow, especially if you can’t reproduce that slowness in your development or staging environment.


## Advantages [_advantages]

* **No need to know what methods to monitor**: Find slow methods without specifying specific method names up front. The profiler automatically bubbles up slow methods as spans in the APM app.
* **Low overhead. Production ready**: The profiler-based approach is designed to be low-overhead enough to run in production; Continuously run it to provide insights into slow methods.


## How to enable inferred spans with async-profiler [_how_to_enable_inferred_spans_with_async_profiler]

Enable inferred spans by setting [`profiling_inferred_spans_enabled`](/reference/config-profiling.md#config-profiling-inferred-spans-enabled) to `true`.

**Tune stack trace frequency**

Tune the frequency at which stack traces are gathered within a profiling session by adjusting [`profiling_inferred_spans_sampling_interval`](/reference/config-profiling.md#config-profiling-inferred-spans-sampling-interval). The lower the sampling interval, the higher the accuracy and the level of detail of the inferred spans. Of course, the higher the level of detail, the higher the profiler overhead and the Elasticsearch index sizes. As most of the processing is done in the background, the impact on the response time of user requests is negligible.

**Clean up clutter in the APM app**

Filter out inferred spans that are faster than the configured threshold, and avoid cluttering the APM app with fast-executing methods by setting [`span_min_duration`](/reference/config-core.md#config-span-min-duration).

**Include and exclude specific classes**

Include classes explicitly with [`profiling_inferred_spans_included_classes`](/reference/config-profiling.md#config-profiling-inferred-spans-included-classes); exclude with [`profiling_inferred_spans_excluded_classes`](/reference/config-profiling.md#config-profiling-inferred-spans-excluded-classes). Generally, the fewer classes that are included, the faster and the more memory efficient the processing is.

By default, the classes from the JDK and from most application servers are excluded. This reduces the number of uninteresting inferred spans.

**Example `elasticapm.properties` file with inferred spans enabled**

```properties
profiling_inferred_spans_enabled=true
profiling_inferred_spans_sampling_interval=50ms
profiling_inferred_spans_min_duration=250ms
profiling_inferred_spans_included_classes=org.example.myapp.*
profiling_inferred_spans_excluded_classes=org.example.myapp.ignoreme.*
```


## Caveats [_caveats]

Inferred spans are estimations, not exact measurements. They may start after the method actually started, and end before the method actually ended. This can lead to inconsistencies, all of which are documented in the [apm-profiling-plugin readme](https://github.com/elastic/apm-agent-java/tree/main/apm-agent-plugins/apm-profiling-plugin).

Also note that the very first inferred span in a transaction doesn’t have a stack trace as it’s likely to be untypical - it’s usually the entry point and has a lot of stuff in it about how the server has accepted a request etc. Consecutive inferred spans have a stack trace that go to their parent. This means that long running methods may show as a span from the inferred span mechanism, but not show an associated stack trace.
