---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/method-config-based.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Configuration-based [method-config-based]

Use the [`trace_methods`](/reference/config-core.md#config-trace-methods) configuration option to specify additional methods to instrument. You can match methods via wildcards in the package, class or method name, by their modifier (like public), by a particular annotation, and more. Because you don’t need to modify your source code, this makes it possible to monitor code in 3rd party libraries.

Be careful, it’s easy to overuse `trace_methods` by matching too many methods— hurting both runtime and startup performance. Use in conjunction with [`span_min_duration`](/reference/config-core.md#config-span-min-duration) when setting for entire packages in order to avoid having too many spans in the APM app.

For more information, and examples, see the [`trace_methods`](/reference/config-core.md#config-trace-methods) configuration reference.

