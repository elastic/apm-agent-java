# gRPC plugin

This plugin provides instrumentation for [gRPC](https://grpc.io/) Remote Procedure Call (RPC) framework.

## Features
- incoming unary requests are instrumented as transactions
- outgoing unary requests are instrumented as spans

## Limitations

Only unary method calls are instrumented, streaming (both ways) is not.

TODO : define versions compatibility

## Implementation notes

gRPC relies on [protocol buffers](https://developers.google.com/protocol-buffers), which require generating Java code from
protobuf definitions. IntelliJ (and probably other IDEs) do not add generated test folders as sources in project, thus
the generated Java files are checked in.

Thus, when protobuf files are updated and generated classes needs to be updated, you have to build the project with a specific
`update-grpc` build profile:

```shell script
mvn clean package -Pupdate-grpc
```
