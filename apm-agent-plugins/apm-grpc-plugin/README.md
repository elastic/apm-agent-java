# gRPC plugin

This plugin provides instrumentation for [gRPC](https://grpc.io/) Remote Procedure Call (RPC) framework.

## Features
- incoming unary requests are instrumented as transactions
- outgoing unary requests are instrumented as spans

## Limitations

Only unary method calls are instrumented, streaming (both ways) is not.

TODO : define versions compatibility
