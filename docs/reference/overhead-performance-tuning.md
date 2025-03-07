---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/tuning-and-overhead.html
---

# Overhead and performance tuning [tuning-and-overhead]

* [Agent overhead](#agent-overhead)
* [Tuning the Agent Startup](#tuning-agent-startup)
* [Tuning the Agent](#tuning-agent)
* [Circuit Breaker](#circuit-breaker)


## Agent overhead [agent-overhead]

Any APM Agent will impose overhead. Here are a few different areas where that overhead might be seen.


### Latency [_latency]

Great care is taken to keep code on critical paths as lightweight as possible. For example, the actual reporting of events is done on a background thread.

It is very important that both the average latency, and higher percentiles of latency are low. That’s because a low average latency means nothing if 1% of your requests experiences very poor performance. The main sources of spikes in higher latencies are garbage collection pauses and contended locks.

We take great care to minimize the memory allocations we do in the Java agent as much as possible. For example, instead of allocating new objects, we take them from an object pool and return them to the pool when they are not used anymore. More details on this process can be found [here](https://github.com/elastic/apm-agent-java/blob/main/apm-agent-core/README.md#lifecycle). When it comes to reporting the recorded events, we directly serialize them into the output stream of the request to the APM server while only relying on reusable buffers. This way we can report events without allocating any objects. We do all that in order to not add additional work for the GC which is already busy cleaning up the memory your application is allocating.

The Java agent also uses specialized data structures (LMAX Disruptor and queues from JCTools) when we transfer events across threads. For example, from the application threads which record transactions to the background reporter thread. This is to circumvent problems like lock contention and false sharing you would get from standard JDK data structures like `ArrayBlockingQueue`.

In single-threaded benchmarks, our Java agent imposes an overhead in the order of single-digit microseconds (µs) up to the 99.99th percentile. The benchmarks were run on a Linux machine with an i7-7700 (3.60GHz) on Oracle JDK 10. We are currently working on multi-threaded benchmarks. When disabling header recording, the agent allocates less than one byte for recording an HTTP request and one JDBC (SQL) query, including reporting those events in the background to the APM Server.


### CPU [_cpu]

Even though the Agent does most of its work in the background, serializing and compressing events, along with sending them to the APM Server does actually also add a bit of CPU overhead. If your application is not CPU bound, this shouldn’t matter much. Your application is probably not CPU bound if you do (blocking) network I/O, like communicating with databases or external services.

In a scenario where APM Server can’t handle all of the events, the Agent will drop data so as to not crash your application.


### Memory [_memory]

Unless you have really small heaps, you usually don’t have to increase the heap size for the Java Agent. It has a fairly small and static memory overhead for the object pools, and some small buffers in the order of a couple of megabytes.


### Network [_network]

The Agent requires some network bandwidth, as it needs to send recorded events to the APM server. This is where it’s important to know how many requests your application handles and how many of those you want to record and store. This can be adjusted with the [Sample rate](#tune-sample-rate).


## Tuning the Agent Startup [tuning-agent-startup]

When the Java agent starts, it needs to initialize various components of the agent, connect to the APM server, and instrument any already loaded classes that it has been configured to trace. This takes some time and resources, and if done synchronously on the main thread (which is the default when using `-javaagent`) will delay the start of the application until complete.

We provide several options to tune the startup, targeted at three startup use cases:

1. Immediate synchronous agent start<br> The application needs to have instrumentation immediately applied, regardless of startup time cost - typically because you don’t want to miss any traces/transactions right from the beginning of the application, or some types of actions only happen at initialization and need to be instrumented before the first instance is created (such as setting up Prepared Statements). In this use case, use the `-javaagent` command-line flag as per [Manual setup with `-javaagent` flag](/reference/setup-javaagent.md)
2. Fastest start (asynchronously)<br> The application can accept instrumentation missing before the application starts and also accept missing some initial traces and transactions. In this use case you can attach to the application after startup with [Automatic setup with `apm-agent-attach-cli.jar`](/reference/setup-attach-cli.md) or if you are using the `-javaagent` command-line flag you can start the agent asynchronously by setting the `elastic.apm.start_async` property (since 1.29.0), eg `java -Delastic.apm.start_async ...` (you can use `elastic.apm.delay_agent_premain_ms=0` in earlier versions)
3. Minimized synchronous start<br> The application needs to have instrumentation immediately applied, but needs to minimize the time before the application starts. This requires some tradeoff: in order to reduce the synchronous startup time, the number of instrumentations applied needs to be minimized through the `enable_instrumentations` option. In this use case you should identify the smallest set of instrumentation groups you can accept for your application monitoring, and use the `enable_instrumentations` configuration option detailed in the [configuration guide](/reference/configuration.md). The smallest set of instrumentations can be found in the agent logs after normal termination of the application (since version 1.29.0). In addition to that you can run the agent with logging level set to DEBUG, and view the statistics produced by the agent on normal termination of the application.


## Tuning the Agent [tuning-agent]

The Java agent offers a variety of [configuration options](/reference/configuration.md), some of which can have a significant impact on performance. To make it easy to determine which options impact performance, we’ve tagged certain configuration options in the documentation with *(performance)*.


### Sample rate [tune-sample-rate]

*Sample rate* is the percentage of requests which should be recorded and sent to the APM Server. (For pre-8.0 servers, unsampled requests are sent without contextual information which reduces transfer and storage sizes; from 8.0 unsampled requests are not sent at all.) What is an ideal sample rate? Unfortunately, there’s no one-size-fits-all answer to that question. Sampling comes down to your preferences and your application. The more you want to sample, the more network bandwidth and disk space you’ll need.

It’s important to note that the latency of an application won’t be affected much by the agent, even if you sample at 100%. However, the background reporter thread has some work to do when serializing and gzipping events.

The sample rate can be changed by altering the [`transaction_sample_rate` (performance)](/reference/config-core.md#config-transaction-sample-rate).


### Stack trace collection [_stack_trace_collection]

If a span, e.g., a captured JDBC query, takes longer than 5ms, we capture the stack trace so that you can easily find the code path which lead to the query. Stack traces can be quite long, taking up bandwidth and disk space, and also requiring object allocations. But because we are processing the stack trace asynchronously, it adds very little latency. Upping the [`span_stack_trace_min_duration` (performance)](/reference/config-stacktrace.md#config-span-stack-trace-min-duration) or disabling stack trace collection altogether can gain you a bit of performance if needed.


### Recording headers and cookies [_recording_headers_and_cookies]

By default, the Java agent records all request and response headers, including cookies. Disabling [`capture_headers` (performance)](/reference/config-core.md#config-capture-headers) can save allocations, network bandwidth, and disk space.


## Circuit Breaker [circuit-breaker]

When enabled, the agent periodically polls stress monitors to detect system/process/JVM stress state. If ANY of the monitors detects a stress indication, the agent will become inactive, as if the [`recording`](/reference/config-core.md#config-recording) configuration option has been set to `false`, thus reducing resource consumption to a minimum. When inactive, the agent continues polling the same monitors in order to detect whether the stress state has been relieved. If ALL monitors approve that the system/process/JVM is not under stress anymore, the agent will resume and become fully functional. For fine-grained Circuit Breaker configurations please refer to [Circuit-Breaker](/reference/config-circuit-breaker.md).

