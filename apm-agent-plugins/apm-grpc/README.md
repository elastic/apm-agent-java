# gRPC plugin

This plugin provides instrumentation for [gRPC](https://grpc.io/) Remote Procedure Call (RPC) framework.

## Features
- incoming unary requests are instrumented as transactions
- outgoing unary requests are instrumented as spans

## Limitations

Only unary method calls are instrumented, streaming (both ways) is not.

Implementation is tested against gRPC versions 1.23.0 to 1.27.1

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
 |--apm-grpc-test-1.23.0   -> test app for gRPC 1.23.0 with generated code
 \--apm-grpc-test-1.27.1   -> test app for gRPC 1.27.1 with generated code
```
