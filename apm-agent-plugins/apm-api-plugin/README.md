# Elastic APM API plugin

As the agent is not a library, users don't declare a dependency on the agent itself.
Instead, the agent is added as a `-javaagent` JVM flag.

That's why there is a separate module (`apm-agent-api`),
which contains the public API users declare a dependency on,
in order to customize spans created by the agent or to create custom spans.

The API itself does not have a dependency on the agent,
because the implementation is in the agent jar,
which is added via the `-javaagent` JVM flag.

It also does not even have a `provided` scoped dependency on the agent implementation,
as users might want to disable the agent in certain environments by not adding the `-javaagent` JVM flag.

The agent itself also does not have any dependency on the API,
as the API could end up twice on the classpath.

The consequence is that neither the API nor the agent can access each other's classes.

That's why yhe API module (`apm-agent-api`) users declare a dependency on is implemented as a noop.


If the agent is active (`-javaagent` JVM flag set),
it injects the actual implementation of the API,
turning the noop into an actual working implementation.

This magic is implemented in the following classes:
 - co.elastic.apm.agent.plugin.api.ElasticApmApiInstrumentation
 - co.elastic.apm.agent.plugin.api.SpanInstrumentation
 - co.elastic.apm.agent.plugin.api.TransactionInstrumentation
