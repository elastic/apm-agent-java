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

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class GrpcServerInstrumentation extends ElasticApmInstrumentation {

    // seems that io.grpc.stub.ServerCalls.UnaryRequestMethod.invoke is a good candidate for instrumentation
    // however, because it's an interface, we might need to rely on improvements in the other PR

    // another alternative class would be : io.grpc.ServerCall.Listener which is part of the public API


    // transaction start:
    // ServerCallHandler.startCall (public API)

    // transaction end
    // ServerCall.Listener

    // Idea : use Context.current() to store and carry current request state ? (public API)
    // -> would allow to provide a good link between ServerCallHandler and the listener execution

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

    public static class ServerCallHandlerInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
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

//        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
//        private static void onExit(@Advice.Thrown Throwable thrown) {
//            System.out.println("server call handler context [exit]= " + Context.current());
//        }

    }

    public static class ServerCallListenerInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
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
        private static void onExit(@Advice.Thrown Throwable thrown) {
            // when there is a runtime exception thrown in one of the listener methods the calling code will catch it
            // and set 'unknown' status, we just replicate this behavior as we don't instrument the part that does this
            if (thrown != null) {
                endTransaction(Status.UNKNOWN, thrown);
            }
        }

    }

    public static class ServerCallInstrumentation extends GrpcServerInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameStartsWith("io.grpc")
                .and(nameContains("ServerCallImpl"));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("io.grpc.ServerCall"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown Throwable thrown, @Advice.Argument(0) Status status) {
            endTransaction(status, null);
        }
    }

    @VisibleForAdvice
    public static void endTransaction(Status status, Throwable thrown) {
        if(tracer == null){
            return;
        }
        Transaction transaction = tracer.currentTransaction();
        if (transaction != null && transaction.getResult() == null) {
            // transaction might be terminated early in case of thrown exception
            transaction
                .withResult(status.getCode().name())
                .captureException(thrown)
                .deactivate()
                .end();
        }
    }
}
