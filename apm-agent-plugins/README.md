# Agent Plugins

This folder contains agent plugins.
A plugin most commonly is an instrumentation for a specific framework or technology.
To add a new plugin,
follow these steps:

1. Create a new maven sub-module in `apm-agent-plugins`
1. Add the module in the `<modules>` section of [`apm-agent-plugins/pom.xml`](pom.xml)
1. Add a dependency to your new module in [`elastic-apm-agent/pom.xml`](../elastic-apm-agent/pom.xml)
   to make sure it is included in the agent jar
1. If you need to add any new runtime dependencies for the agent, make sure to properly configure shading in 
   [`elastic-apm-agent/pom.xml`](../elastic-apm-agent/pom.xml)
1. Properly test your module with unit tests and consider adding integration tests.
   See [`CONTRIBUTING.md`](../CONTRIBUTING.md#coding-guidelines) for more guidance.
1. Add information about the supported technology in [`docs/intro.asciidoc`](../docs/intro.asciidoc)
1. Add a line in [`CHANGELOG.md`](../CHANGELOG.md) 


NOTE: when adding advices, make sure to add `(suppress = Throwable.class)`
to the `net.bytebuddy.asm.Advice.OnMethodEnter` and `net.bytebuddy.asm.Advice.OnMethodExit` annotations. 
Search for the regex `@.*OnMethod(Enter|Exit)(?!\(s)` to find annotations with a missing `suppress` attribute. 

