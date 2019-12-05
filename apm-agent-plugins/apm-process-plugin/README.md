# Process plugin

This plugin creates spans for external processes executed by the JVM, which use the `java.lang.Process` class.

[Apache commons-exec](https://commons.apache.org/proper/commons-exec/) library support is included.

## Limitations

`java.lang.ProcessHandler` and `java.lang.Process.toHandle()` introduced in java 9 are not
instrumented. As a result, process execution using this API is not yet supported.

## Implementation Notes

Instrumentation of classes in `java.lang.*` that are loaded in the bootstrap classloader can't
be tested with unit tests due to the fact that agent is loaded in application/system classloader
for those tests.

Thus, using integration tests is required to test the instrumentation end-to-end.
Also, in order to provide a good test of this feature without testing everything in integration tests, we
- delegate most of the advice code to helper classes
- test extensively those classes with regular unit tests
