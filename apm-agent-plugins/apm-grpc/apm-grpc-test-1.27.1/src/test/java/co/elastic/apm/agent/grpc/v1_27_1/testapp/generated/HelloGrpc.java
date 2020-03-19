/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.grpc.v1_27_1.testapp.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.27.1)",
    comments = "Source: rpc.proto")
public final class HelloGrpc {

  private HelloGrpc() {}

  public static final String SERVICE_NAME = "helloworld.Hello";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SayHello",
      requestType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.class,
      responseType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloMethod() {
    io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloMethod;
    if ((getSayHelloMethod = HelloGrpc.getSayHelloMethod) == null) {
      synchronized (HelloGrpc.class) {
        if ((getSayHelloMethod = HelloGrpc.getSayHelloMethod) == null) {
          HelloGrpc.getSayHelloMethod = getSayHelloMethod =
              io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SayHello"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.getDefaultInstance()))
              .setSchemaDescriptor(new HelloMethodDescriptorSupplier("SayHello"))
              .build();
        }
      }
    }
    return getSayHelloMethod;
  }

  private static volatile io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayManyHelloMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SayManyHello",
      requestType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.class,
      responseType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
  public static io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayManyHelloMethod() {
    io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayManyHelloMethod;
    if ((getSayManyHelloMethod = HelloGrpc.getSayManyHelloMethod) == null) {
      synchronized (HelloGrpc.class) {
        if ((getSayManyHelloMethod = HelloGrpc.getSayManyHelloMethod) == null) {
          HelloGrpc.getSayManyHelloMethod = getSayManyHelloMethod =
              io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SayManyHello"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.getDefaultInstance()))
              .setSchemaDescriptor(new HelloMethodDescriptorSupplier("SayManyHello"))
              .build();
        }
      }
    }
    return getSayManyHelloMethod;
  }

  private static volatile io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloManyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SayHelloMany",
      requestType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.class,
      responseType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloManyMethod() {
    io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloManyMethod;
    if ((getSayHelloManyMethod = HelloGrpc.getSayHelloManyMethod) == null) {
      synchronized (HelloGrpc.class) {
        if ((getSayHelloManyMethod = HelloGrpc.getSayHelloManyMethod) == null) {
          HelloGrpc.getSayHelloManyMethod = getSayHelloManyMethod =
              io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SayHelloMany"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.getDefaultInstance()))
              .setSchemaDescriptor(new HelloMethodDescriptorSupplier("SayHelloMany"))
              .build();
        }
      }
    }
    return getSayHelloManyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SayHelloStream",
      requestType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.class,
      responseType = co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
      co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloStreamMethod() {
    io.grpc.MethodDescriptor<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> getSayHelloStreamMethod;
    if ((getSayHelloStreamMethod = HelloGrpc.getSayHelloStreamMethod) == null) {
      synchronized (HelloGrpc.class) {
        if ((getSayHelloStreamMethod = HelloGrpc.getSayHelloStreamMethod) == null) {
          HelloGrpc.getSayHelloStreamMethod = getSayHelloStreamMethod =
              io.grpc.MethodDescriptor.<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest, co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SayHelloStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply.getDefaultInstance()))
              .setSchemaDescriptor(new HelloMethodDescriptorSupplier("SayHelloStream"))
              .build();
        }
      }
    }
    return getSayHelloStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static HelloStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HelloStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HelloStub>() {
        @java.lang.Override
        public HelloStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HelloStub(channel, callOptions);
        }
      };
    return HelloStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HelloBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HelloBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HelloBlockingStub>() {
        @java.lang.Override
        public HelloBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HelloBlockingStub(channel, callOptions);
        }
      };
    return HelloBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static HelloFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HelloFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HelloFutureStub>() {
        @java.lang.Override
        public HelloFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HelloFutureStub(channel, callOptions);
        }
      };
    return HelloFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class HelloImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public void sayHello(co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnimplementedUnaryCall(getSayHelloMethod(), responseObserver);
    }

    /**
     * <pre>
     * client streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest> sayManyHello(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      return asyncUnimplementedStreamingCall(getSayManyHelloMethod(), responseObserver);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public void sayHelloMany(co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnimplementedUnaryCall(getSayHelloManyMethod(), responseObserver);
    }

    /**
     * <pre>
     * bidi streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest> sayHelloStream(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      return asyncUnimplementedStreamingCall(getSayHelloStreamMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSayHelloMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO)))
          .addMethod(
            getSayManyHelloMethod(),
            asyncClientStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_MANY_HELLO)))
          .addMethod(
            getSayHelloManyMethod(),
            asyncServerStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO_MANY)))
          .addMethod(
            getSayHelloStreamMethod(),
            asyncBidiStreamingCall(
              new MethodHandlers<
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest,
                co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>(
                  this, METHODID_SAY_HELLO_STREAM)))
          .build();
    }
  }

  /**
   */
  public static final class HelloStub extends io.grpc.stub.AbstractAsyncStub<HelloStub> {
    private HelloStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public void sayHello(co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSayHelloMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * client streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest> sayManyHello(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      return asyncClientStreamingCall(
          getChannel().newCall(getSayManyHelloMethod(), getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public void sayHelloMany(co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request,
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(getSayHelloManyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * bidi streaming
     * </pre>
     */
    public io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest> sayHelloStream(
        io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> responseObserver) {
      return asyncBidiStreamingCall(
          getChannel().newCall(getSayHelloStreamMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   */
  public static final class HelloBlockingStub extends io.grpc.stub.AbstractBlockingStub<HelloBlockingStub> {
    private HelloBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply sayHello(co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request) {
      return blockingUnaryCall(
          getChannel(), getSayHelloMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * server streaming
     * </pre>
     */
    public java.util.Iterator<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> sayHelloMany(
        co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request) {
      return blockingServerStreamingCall(
          getChannel(), getSayHelloManyMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class HelloFutureStub extends io.grpc.stub.AbstractFutureStub<HelloFutureStub> {
    private HelloFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * unary method call
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply> sayHello(
        co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getSayHelloMethod(), getCallOptions()), request);
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
          serviceImpl.sayHello((co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest) request,
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>) responseObserver);
          break;
        case METHODID_SAY_HELLO_MANY:
          serviceImpl.sayHelloMany((co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest) request,
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>) responseObserver);
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
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>) responseObserver);
        case METHODID_SAY_HELLO_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sayHelloStream(
              (io.grpc.stub.StreamObserver<co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class HelloBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    HelloBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.Rpc.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Hello");
    }
  }

  private static final class HelloFileDescriptorSupplier
      extends HelloBaseDescriptorSupplier {
    HelloFileDescriptorSupplier() {}
  }

  private static final class HelloMethodDescriptorSupplier
      extends HelloBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    HelloMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
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
              .setSchemaDescriptor(new HelloFileDescriptorSupplier())
              .addMethod(getSayHelloMethod())
              .addMethod(getSayManyHelloMethod())
              .addMethod(getSayHelloManyMethod())
              .addMethod(getSayHelloStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
