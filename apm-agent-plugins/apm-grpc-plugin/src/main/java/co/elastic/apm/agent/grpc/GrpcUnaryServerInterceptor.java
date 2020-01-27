package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class GrpcUnaryServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> HEADER_KEY = Metadata.Key.of(TraceContext.TRACE_PARENT_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (ElasticApmInstrumentation.tracer != null) {
            // get upstream header if it exists
            ElasticApmInstrumentation.tracer.startTransaction(TraceContext.fromTraceparentHeader(), headers.get(HEADER_KEY), GrpcUnaryServerInterceptor.class.getClassLoader())
                .withName(call.getMethodDescriptor().getFullMethodName())
                .withType("request")
                .activate();
        } else {
            next.startCall(call, headers);
        }

        return next.startCall(new InterceptedUnaryMethodCall<ReqT, RespT>(call), headers);
    }

    private static class InterceptedUnaryMethodCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {

        protected InterceptedUnaryMethodCall(ServerCall<ReqT, RespT> call) {
            super(call);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            delegate().close(status, trailers);

            if (ElasticApmInstrumentation.tracer != null) {
                Transaction transaction = ElasticApmInstrumentation.tracer.currentTransaction();
                if (transaction != null) {
                    transaction.withResult(status.getCode().toString())
                        .deactivate()
                        .end();
                }
            }
        }
    }

}
