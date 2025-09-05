---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/method-annotations.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Annotations [method-annotations]

The [annotation API](/reference/public-api.md#api-annotation) allows you to place annotations on top of methods to automatically create spans for them. This method of creating spans is easier, more robust, and typically more performant than using the API; there’s nothing you can do wrong like forgetting to end a span or close a scope.

Annotations are less flexible when used on their own, but can be combined with the span API for added flexibility.


## How-to create spans with the annotations API [_how_to_create_spans_with_the_annotations_api]

Here’s an example that uses the [`@CaptureSpan`](/reference/public-api.md#api-capture-span) annotation to create a span for the `spanWithAnnotation()` method. The span is named `spanName`, is of type `ext`, and subtype `http`.

```java
@CaptureSpan(value = "spanName", type = "ext", subtype = "http")
private static void spanWithAnnotation() {
    // do your thing...
}
```


## Combine with the span API [_combine_with_the_span_api]

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


