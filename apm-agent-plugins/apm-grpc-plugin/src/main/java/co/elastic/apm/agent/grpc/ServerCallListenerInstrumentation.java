package co.elastic.apm.agent.grpc;

import io.grpc.Status;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments implementations of {@link io.grpc.ServerCall.Listener} to detect runtime exceptions
 * <ul>
 *     <li>{@link io.grpc.ServerCall.Listener#onMessage(Object)}</li>
 *     <li>{@link io.grpc.ServerCall.Listener#onHalfClose()}</li>
 *     <li>{@link io.grpc.ServerCall.Listener#onCancel()}</li>
 *     <li>{@link io.grpc.ServerCall.Listener#onComplete()}</li>
 * </ul>
 */
public class ServerCallListenerInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            .and(nameContains("Unary"));
    }

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
        if( null == tracer){
            return;
        }


        // when there is a runtime exception thrown in one of the listener methods the calling code will catch it
        // and set 'unknown' status, we just replicate this behavior as we don't instrument the part that does this
        if (thrown != null) {
            GrpcHelper.endTransaction(Status.UNKNOWN, thrown, tracer.currentTransaction());
        }
    }

}
