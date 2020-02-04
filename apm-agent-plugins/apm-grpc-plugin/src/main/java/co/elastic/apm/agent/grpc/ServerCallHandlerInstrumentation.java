package co.elastic.apm.agent.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments implementations of {@link io.grpc.ServerCallHandler#startCall(ServerCall, Metadata)} in order to start
 * transaction.
 */
public class ServerCallHandlerInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            .and(nameContains("Unary"));
    }

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

        GrpcHelper.startTransaction(tracer, clazz.getClassLoader(), serverCall, headers);
    }

}
