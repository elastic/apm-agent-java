# apm-agent-java

This project is responsible for creating the distribution i.e. the javaagent jar file which can be used when specifying the
`-javaagent` JVM parameter.
This jar contains all plugins,
including all dependencies (a so-called uber jar).

Note: attaching with `-javaagent` is not yet available. Instrumentation has to be set up manually at the moment.

In order to avoid dependency conflicts, all dependencies are relocated into the namespace co.elastic.apm.shaded.
