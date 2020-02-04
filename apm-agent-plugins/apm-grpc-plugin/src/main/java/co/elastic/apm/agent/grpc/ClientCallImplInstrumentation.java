package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.impl.transaction.Span;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class ClientCallImplInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            // matches all the implementations of ClientCall available in io.grpc package
            .and(nameEndsWith("ClientCallImpl"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // pre-filtering is used to make this quite fast and limited to gRPC packages
        return hasSuperType(named("io.grpc.ClientCall"));
    }

    /**
     * Instruments {@code ClientCallImpl} constructor to build client call exit span.
     */
    public static class Constructor extends ClientCallImplInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor().and(takesArgument(0, MethodDescriptor.class));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(
            @Nullable @Advice.Argument(0) MethodDescriptor<?, ?> method,
            @Advice.Local("span") Span span) {

            if (tracer == null) {
                return;
            }

            span = GrpcHelper.createExitSpanAndActivate(tracer.currentTransaction(), method);

        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onExit(@Advice.This ClientCall<?, ?> clientCall,
                                   @Advice.Local("span") @Nullable Span span) {

            GrpcHelper.registerSpanAndDeactivate(span, clientCall);

        }
    }

    /**
     * Instruments {@code ClientCallImpl#start} to start client call span
     */
    public static class Start extends ClientCallImplInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("start");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ClientCall<?, ?> clientCall) {
            GrpcHelper.startSpan(clientCall);
        }

    }

    /**
     * Instruments {@code ClientCallImpl#halfClose} to terminate client call span
     */
    public static class End extends ClientCallImplInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("halfClose");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall<?, ?> clientCall,
                                   @Advice.Thrown Throwable thrown) {

            GrpcHelper.endSpan(clientCall);

        }
    }
}
