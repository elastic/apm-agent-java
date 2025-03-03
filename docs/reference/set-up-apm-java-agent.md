---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/setup.html
---

# Set up the APM Java Agent [setup]

There are three different ways to set up the Elastic APM Java Agent:

1. [Manual setup with `-javaagent` flag](/reference/setup-javaagent.md)<br> Manually set up and configure the agent with the `-javaagent` JVM option. No application code change required, requires application restart.
2. [Automatic setup with `apm-agent-attach-cli.jar`](/reference/setup-attach-cli.md)<br> Automatically set up the agent without needing to alter the configuration of your JVM or application server. No application code nor JVM options changes required, allows attaching to a running JVM.
3. [Programmatic API setup to self-attach](/reference/setup-attach-api.md)<br> Set up the agent with a one-line code change and an extra `apm-agent-attach` dependency. No modification of JVM options, the agent artifact is embedded within the packaged application binary.


## Configuration [get-started-configuration]

Once you’ve set up the Agent, see the [configuration guide](/reference/configuration.md) on how to configure Elastic APM.


## SSL/TLS communication with APM Server [ssl-setup]

If [SSL/TLS communication](docs-content://solutions/observability/apps/apm-agent-tls-communication.md) is enabled on the APM Server, make sure to check out the [SSL setup guide](/reference/ssl-configuration.md).


## Monitoring AWS Lambda Functions (Experimental) [aws-lambda-setup]

Learn how to set up AWS Lambda functions tracing in our [Lambda setup guide](/reference/aws-lambda.md).


## Using with Security Manager enabled [security-manager]

The agent should work as expected on JVMs with an enabled `SecurityManager`, provided that it is granted with `java.security.AllPermission`. Make sure that the following snippet is added to an effective* policy (replace with the real path** to the agent jar):

```
grant codeBase "file:</path/to/elastic-apm-agent.jar>" {
    permission java.security.AllPermission;
};
```

If you see a `java.lang.SecurityException` exception (for example a - `java.security.AccessControlException`) after verifying the above `grant` snippet is effectively applied, open an issue in our [GitHub repo](https://github.com/elastic/apm-agent-java) with a description and the full stack trace.

* it is possible to have multiple policy files taking effect at the same time on a single JVM. The policy entry above can be added to an existing policy or can be appended through the `java.security.policy` system property. See [documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/security/PolicyFiles.md) for more details.

** you can make use of the [property expansion](https://docs.oracle.com/javase/8/docs/technotes/guides/security/PolicyFiles.md#PropertyExp) capability for specifying the agent jar path.


## Supported technologies [supported-technologies]

Please check [supported technologies](/reference/supported-technologies.md) for details on if the Elastic APM agent supports auto-instrumentation of the technologies your application is using.


## Bootstrap checks [bootstrap-checks]

In some cases, the agent needs to take a decision to abort very early, before almost any initialization process takes place, for example- when it is attached to a non-supported JVM version. This decision is based on what we call "bootstrap checks". If any of the bootstrap checks fails, the agent will log an error to the standard error stream and abort. It is possible to disable bootstrap checks by setting the `elastic.apm.disable_bootstrap_checks` System property, or the `ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS` environment variable, to `true`.


### JVM Filtering [jvm-filtering]

In some cases, users may cast a too-wide net to instrument their Java processes, for example when setting the `JAVA_TOOL_OPTIONS` environment variable globally on a host/container on which many JVMs run. In such cases, users may want to exclude JVMs from being instrumented, or to specifically allow when necessary. For this purpose, we have the following bootstrap configuration options available:

| System property name | Env variable name | Description |
| --- | --- | --- |
| `elastic.apm.bootstrap_allowlist` | `ELASTIC_APM_BOOTSTRAP_ALLOWLIST` | If set, the agent will be enabled **only** on JVMs of which command matches one of the patterns in the provided list |
| `elastic.apm.bootstrap_exclude_list` | `ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST` | If set, the agent will be disabled on JVMs that contain a System property with one of the provided names in the list |

The allowlist option expects a comma-separated list of wild-card patterns. Such patterns may contain wildcards (`*`), which match zero or more characters. Examples: `foo\*bar\*baz\*, \*foo\*`. Matching is case-insensitive by default. Prepending an element with `(?-i)}` makes the matching case-sensitive. The patterns are matched against the JVM command as it is stored in the `sun.java.command` system property.

Some examples:

1. Allow JVM attachment only on Tomcat and a proprietary Java app:<br> `-Delastic.apm.bootstrap_allowlist=*org.apache.catalina.startup.Bootstrap*,my.cool.app.*`
2. Disable when some custom System properties are set:<br> `-Delastic.apm.bootstrap_exclude_list=custom.property.1,custom.property.2`






