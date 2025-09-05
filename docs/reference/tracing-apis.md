---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/apis.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Tracing APIs [apis]

There are three different ways enhance the out-of-the-box instrumentation of the Java agent with manual instrumentation:

1. [Public API](/reference/public-api.md)<br> A simple and stable API that is most native to the agent. Contains annotations to declaratively create spans.
2. [OpenTelemetry bridge](/reference/opentelemetry-bridge.md)<br> A vendor neutral API. If you plan to do a lot of manual instrumentation and want to reduce vendor lock-in this is probably what you’re looking for.
3. [OpenTracing bridge](/reference/opentracing-bridge.md)<br> A vendor neutral API that is discontinued in favor of OpenTelemetry.

A further option is the [plugin api](/reference/plugin-api.md) which uses the OpenTelemetry API and allows you to add in custom instrumentation without modifying the application.


## Operation Modes [apis-operation-modes]

All APIs allow for different operation modes in combination with the Elastic APM agent

Noop
:   If the agent is not installed, the APIs are in noop mode and do not actually record and report spans.


Mix and Match
:   If you want to leverage the auto instrumentation of Elastic APM, but also want to create custom spans or use the API to add custom labels to the spans created by Elastic APM, you can just do that.


Manual instrumentation
:   If you don’t want Elastic APM to auto-instrument known frameworks, but instead only rely on manual instrumentation, disable the auto instrumentation setting the configuration option [`instrument`](/reference/config-core.md#config-instrument) to `false`.





