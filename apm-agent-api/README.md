# Public API for the Elastic APM Java agent

This module represents the public API of Elastic APM Java.
Use this to add custom data to transactions for example.

If the application is started without the Elastic APM `-javaagent` set,
this API acts as a noop implementation.
If the agent is enabled,
it injects the actual implementation when the `co.elastic.apm.api.ElasticApm` class is loaded.
