package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments server-side gRPC unary calls as transactions.
 * <br>
 * As request processing is done on a single thread, using thread local context propagation is being used.
 */
public abstract class GrpcServerInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Metadata.Key<String> HEADER_KEY = Metadata.Key.of(TraceContext.TRACE_PARENT_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            .and(nameContains("Unary"));
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("grpc");
    }

    /**
     * Instruments implementations of {@link io.grpc.ServerCallHandler#startCall(ServerCall, Metadata)} in order to start
     * transaction.
     */
    public static class ServerCallHandlerInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            // pre-filtering is used to make this quite fast and limited to gRPC packages
            return hasSuperType(named("io.grpc.ServerCallHandler"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("startCall");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(
            @Advice.Origin Class<?> clazz,
            @Advice.Argument(0) ServerCall<?, ?> serverCall,
            @Advice.Argument(1) Metadata headers) {

            if (tracer == null) {
                return;
            }

            tracer.startTransaction(TraceContext.fromTraceparentHeader(), headers.get(HEADER_KEY), clazz.getClassLoader())
                .withName(serverCall.getMethodDescriptor().getFullMethodName())
                .withType("request")
                .activate();
        }

    }

    /**
     * Instruments implementations of {@link io.grpc.ServerCall.Listener} to detect runtime exceptions
     * <ul>
     *     <li>{@link io.grpc.ServerCall.Listener#onMessage(Object)}</li>
     *     <li>{@link io.grpc.ServerCall.Listener#onHalfClose()}</li>
     *     <li>{@link io.grpc.ServerCall.Listener#onCancel()}</li>
     *     <li>{@link io.grpc.ServerCall.Listener#onComplete()}</li>
     * </ul>
     */
    public static class ServerCallListenerInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            // pre-filtering is used to make this quite fast and limited to gRPC packages
            return hasSuperType(named("io.grpc.ServerCall$Listener"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            // message received --> indicates RPC start for unary call
            // actual method invocation is delayed until 'half close'
            return named("onMessage")
                //
                // client completed all message sending, but can still cancel the call
                // --> for unary calls, actual method invocation is done here (but it's an impl. detail)
                .or(named("onHalfClose"))
                //
                // call cancelled by client (or network issue)
                // --> end of unary call (error)
                .or(named("onCancel"))
                //
                // call complete (but client not guaranteed to get all messages)
                // --> end of unary call (success)
                .or(named("onComplete"));
        }


        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown @Nullable Throwable thrown) {
            // when there is a runtime exception thrown in one of the listener methods the calling code will catch it
            // and set 'unknown' status, we just replicate this behavior as we don't instrument the part that does this
            if (thrown != null) {
                endTransaction(Status.UNKNOWN, thrown);
            }
        }

    }

    /**
     * Instruments {@link ServerCall#close(Status, Metadata)} for successful server call execution.
     * Runtime exceptions during call execution are handled with {@link ServerCallListenerInstrumentation}
     */
    public static class ServerCallInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameStartsWith("io.grpc")
                .and(nameContains("ServerCall"));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            // pre-filtering is used to make this quite fast and limited to gRPC packages
            return hasSuperType(named("io.grpc.ServerCall"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown @Nullable Throwable thrown, @Advice.Argument(0) Status status) {
            endTransaction(status, thrown);
        }
    }

    @VisibleForAdvice
    public static void endTransaction(Status status, @Nullable Throwable thrown) {
        if (tracer == null) {
            return;
        }
        Transaction transaction = tracer.currentTransaction();
        if (transaction != null && transaction.getResult() == null) {
            // transaction might be terminated early in case of thrown exception
            // from method signature it's a runtime exception, thus very likely an issue in server implementation
            transaction
                .withResult(status.getCode().name())
                .captureException(thrown)
                .deactivate()
                .end();
        }
    }
}
