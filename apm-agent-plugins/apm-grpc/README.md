# gRPC plugin

This plugin provides instrumentation for [gRPC](https://grpc.io/) Remote Procedure Call (RPC) framework.

## Features
- incoming unary requests are instrumented as transactions
- outgoing unary requests are instrumented as spans

## Limitations

Only unary method calls are instrumented, streaming (both ways) is not.

Implementation have been tested against versions between 1.6.1 and latest.
Versions 1.5.0 and below are not supported yet.
Latest 1.x version is checked at every build

## Implementation notes

gRPC relies on [protocol buffers](https://developers.google.com/protocol-buffers), which require generating Java code from
protobuf definitions. IntelliJ (and probably other IDEs) do not add generated test folders as sources in project, thus
the generated Java files are checked in.

Thus, when protobuf files are updated and generated classes needs to be updated, you have to build the project with a specific
`update-grpc` build profile:

```shell script
mvn clean package -Pupdate-grpc
```

Due to usage of generated code, testing multiple versions requires to have a separate maven module per version,
as a result, we have the following folder structure:

```
apm-grpc                   -> agent plugin itself + common test infrastructure
 |--apm-grpc-test-1.6.1    -> test app for gRPC 1.6.1 with generated code
 \--apm-grpc-test-latest   -> test app for gRPC latest with generated code
```

## Test applications

Standalone gRPC test applications are available for testing as executable jars.
It allows testing application behavior with/without agent outside of unit/integration tests.

There is one version per test submodule
```
java -jar apm-grpc-test-1.6.1/target/testapp.jar
java -jar apm-grpc-test-latest/target/testapp.jar
```

Those applications support a few command-line arguments
```
--port 4242    # sets server port to use
--benchmark    # enables 'benchmark mode': adds a warm-up and disables verbose output
--count 100    # sets number of iterations to execute
```
