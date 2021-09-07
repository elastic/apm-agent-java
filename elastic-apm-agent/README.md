# elastic-apm-agent

This project is responsible for creating the distribution i.e. the javaagent jar file which can be used when specifying the
`-javaagent` JVM parameter.

It contains the premain and agentmain method and loads the actual agent (apm-agent) in an isolated class loader hierarchy.
