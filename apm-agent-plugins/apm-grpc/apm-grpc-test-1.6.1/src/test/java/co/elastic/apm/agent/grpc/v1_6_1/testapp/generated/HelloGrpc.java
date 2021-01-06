/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.grpc.v1_6_1.testapp.generated;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.6.1)",
    comments = "Source: rpc.proto")
public final class HelloGrpc {

  private HelloGrpc() {}

  public static final String SERVICE_NAME = "helloworld.Hello";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> METHOD_SAY_HELLO =
      io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(generateFullMethodName(
              "helloworld.Hello", "SayHello"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply.getDefaultInstance()))
          .build();
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> METHOD_SAY_MANY_HELLO =
      io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
          .setFullMethodName(generateFullMethodName(
              "helloworld.Hello", "SayManyHello"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply.getDefaultInstance()))
          .build();
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> METHOD_SAY_HELLO_MANY =
      io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
          .setFullMethodName(generateFullMethodName(
              "helloworld.Hello", "SayHelloMany"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply.getDefaultInstance()))
          .build();
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> METHOD_SAY_HELLO_STREAM =
      io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
          .setFullMethodName(generateFullMethodName(
              "helloworld.Hello", "SayHelloStream"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply.getDefaultInstance()))
          .build();

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static HelloStub newStub(io.grpc.Channel channel) {
    return new HelloStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HelloBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new HelloBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static HelloFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new HelloFutureStub(channel);
  }

  /**
   */
  public static abstract class HelloImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public void sayHello(co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_SAY_HELLO, responseObserver);
    }

    /**
     * <pre>
     * client streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest> sayManyHello(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      return asyncUnimplementedStreamingCall(METHOD_SAY_MANY_HELLO, responseObserver);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public void sayHelloMany(co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_SAY_HELLO_MANY, responseObserver);
    }

    /**
     * <pre>
     * bidi streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest> sayHelloStream(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      return asyncUnimplementedStreamingCall(METHOD_SAY_HELLO_STREAM, responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_SAY_HELLO,
            asyncUnaryCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO)))
          .addMethod(
            METHOD_SAY_MANY_HELLO,
            asyncClientStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_MANY_HELLO)))
          .addMethod(
            METHOD_SAY_HELLO_MANY,
            asyncServerStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO_MANY)))
          .addMethod(
            METHOD_SAY_HELLO_STREAM,
            asyncBidiStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO_STREAM)))
          .build();
    }
  }

  /**
   */
  public static final class HelloStub extends io.grpc.stub.AbstractStub<HelloStub> {
    private HelloStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HelloStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public void sayHello(co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_SAY_HELLO, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * client streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest> sayManyHello(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      return asyncClientStreamingCall(
          getChannel().newCall(METHOD_SAY_MANY_HELLO, getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public void sayHelloMany(co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(METHOD_SAY_HELLO_MANY, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * bidi streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest> sayHelloStream(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> responseObserver) {
      return asyncBidiStreamingCall(
          getChannel().newCall(METHOD_SAY_HELLO_STREAM, getCallOptions()), responseObserver);
    }
  }

  /**
   */
  public static final class HelloBlockingStub extends io.grpc.stub.AbstractStub<HelloBlockingStub> {
    private HelloBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HelloBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply sayHello(co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_SAY_HELLO, getCallOptions(), request);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public java.util.Iterator<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> sayHelloMany(
        co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request) {
      return blockingServerStreamingCall(
          getChannel(), METHOD_SAY_HELLO_MANY, getCallOptions(), request);
    }
  }

  /**
   */
  public static final class HelloFutureStub extends io.grpc.stub.AbstractStub<HelloFutureStub> {
    private HelloFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HelloFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply> sayHello(
        co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_SAY_HELLO, getCallOptions()), request);
    }
  }

  private static final int METHODID_SAY_HELLO = 0;
  private static final int METHODID_SAY_HELLO_MANY = 1;
  private static final int METHODID_SAY_MANY_HELLO = 2;
  private static final int METHODID_SAY_HELLO_STREAM = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final HelloImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(HelloImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SAY_HELLO:
          serviceImpl.sayHello((co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest) request,
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>) responseObserver);
          break;
        case METHODID_SAY_HELLO_MANY:
          serviceImpl.sayHelloMany((co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest) request,
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SAY_MANY_HELLO:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sayManyHello(
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>) responseObserver);
        case METHODID_SAY_HELLO_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sayHelloStream(
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static final class HelloDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier {
    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.Rpc.getDescriptor();
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (HelloGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new HelloDescriptorSupplier())
              .addMethod(METHOD_SAY_HELLO)
              .addMethod(METHOD_SAY_MANY_HELLO)
              .addMethod(METHOD_SAY_HELLO_MANY)
              .addMethod(METHOD_SAY_HELLO_STREAM)
              .build();
        }
      }
    }
    return result;
  }
}
