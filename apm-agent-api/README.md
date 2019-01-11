# Public API for the Elastic APM Java agent

This module represents the public API of Elastic APM Java.
Use this to add custom data to transactions for example.

If the application is started without the Elastic APM `-javaagent` set,
this API acts as a noop implementation.
If the agent is enabled,
it injects the actual implementation when the `co.elastic.apm.api.ElasticApm` class is loaded.

## Public API vs internal API
Why do we need a separate public API?

### Separation makes sense
- Two target groups: agent devs and application devs
- Public API
    - Simplified version of the API but offers less control
    - Has to be super-stable
    - Is simpler to use and harder to misuse
    - Everything has non-null return types, returning noops instead of `null`
    - Limited in scope
        - it is not possible to access all properties the intake API offers
        - Focused on customizing spans created by auto instrumentation and creating lightweight custom spans
- Internal API
    - Can change on each commit -> changing the internal API is not considered a breaking change
    - Is focused on performance and reduction of garbage
    - Focused on deep integration with frameworks
    - Supports all relevant properties available in the intake API

If the public API is not enough, consider contributing a new plugin module [`../apm-agent-plugins/README.md`](../apm-agent-plugins/README.md).

### Separation is technically necessary
- The java agent is not a library
- It’s attached via `-javaagent`
- Applications can’t access the internal API without reflection
- API is an optional library
    - Noop if no `-javaagent`
    - Agent brings the API to life by bridging from the public API to the internal API
