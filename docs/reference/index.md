---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/intro.html
  - https://www.elastic.co/guide/en/apm/agent/java/current/index.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# APM Java agent [intro]

The Elastic APM Java Agent automatically measures the performance of your application and tracks errors. It has built-in support for popular frameworks and technologies, as well as a simple [API](/reference/public-api.md) which allows you to instrument any application, and a [Plugin API](/reference/plugin-api.md) that allows you to add custom instrumentation.

::::{note}
The minimum required version of the APM Server is 6.5.0
::::



## How does the Agent work? [how-it-works]

The Agent auto-instruments [Supported technologies](/reference/set-up-apm-java-agent.md#supported-technologies) and records interesting events, like spans for database queries and transactions for incoming HTTP requests. To do this, it leverages the capability of the JVM to instrument the bytecode of classes. This means that for the supported technologies, there are no code changes required.

Spans are grouped in transactions — by default, one for each incoming HTTP request. But it’s possible to create custom transactions not associated with an HTTP request. Transactions and Spans are sent to the APM Server, where they’re converted to a format suitable for Elasticsearch. You can then use the APM app in Kibana to gain insight into latency issues and error culprits within your application.

More detailed information on how the Agent works can be found in the [FAQ](/reference/frequently-asked-questions.md#faq-how-does-it-work).


## Additional components [additional-components]

APM Agents work in conjunction with the [APM Server](docs-content://solutions/observability/apm/index.md), [Elasticsearch](docs-content://get-started/index.md), and [Kibana](docs-content://get-started/the-stack.md). The [APM Guide](docs-content://solutions/observability/apm/index.md) provides details on how these components work together, and provides a matrix outlining [Agent and Server compatibility](docs-content://solutions/observability/apm/apm-agent-compatibility.md).

