---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/method-api.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# API/Code [method-api]

Use the [span API](/reference/public-api.md#api-span) to manually create spans for methods of interest. The API is extremely flexible, and offers the ability to customize your spans, by adding labels to them, or by changing the type, name, or timestamp.

::::{tip}
OpenTracing fan? You can use the [OpenTracing API](/reference/opentracing-bridge.md), instead of the Agent API, to manually create spans.
::::



## How to create spans with the span API [_how_to_create_spans_with_the_span_api]

1. Get the current span with [`currentSpan()`](/reference/public-api.md#api-current-span), which may or may not have been created with auto-instrumentation.
2. Create a child span with [`startSpan()`](/reference/public-api.md#api-span-start-span).
3. Activate the span with [`activate()`](/reference/public-api.md#api-span-activate).
4. Customize the span with the [span API](/reference/public-api.md#api-span).

```java
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;

Span parent = ElasticApm.currentSpan(); <1>
Span span = parent.startSpan(); <2>
try (Scope scope = span.activate()) { <3>
    span.setName("SELECT FROM customer"); <4>
    span.addLabel("foo", "bar"); <5>
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

1. Get current span
2. Create a child span
3. Make this span the active span on the current thread
4. Override the default span name
5. Add labels to the span



## Combine with annotations [_combine_with_annotations]

You can combine annotations with the span API to increase their flexibility. Just get the current span on an annotated method and customize the span to your liking.

```java
@CaptureSpan <1>
private static void spanWithAnnotation(String foo) {
    Span span = ElasticApm.currentSpan(); <2>
    span.setTag("foo", foo); <3>
}
```

1. Use `@CaptureSpan` annotation to create a span
2. Get the current span (the one created via the `@CaptureSpan` annotation)
3. Customize the span


