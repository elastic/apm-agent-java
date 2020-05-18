package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class AlibabaRpcContextInstrumentation extends AbstractDubboInstrumentation {

    @VisibleForAdvice
    public static final List<Class<? extends ElasticApmInstrumentation>> RESPONSE_FUTURE_INSTRUMENTATION =
        Collections.<Class<? extends ElasticApmInstrumentation>>singletonList(AlibabaResponseFutureInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.alibaba.dubbo.rpc.RpcContext");
    }

    /**
     * {@link RpcContext#setFuture(java.util.concurrent.Future)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setFuture").and(takesArgument(0, named("java.util.concurrent.Future")));
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onEnter(@Advice.Argument(0) Future<?> future) {
        if (future instanceof FutureAdapter) {
            ElasticApmAgent.ensureInstrumented(((FutureAdapter<?>) future).getFuture().getClass(), RESPONSE_FUTURE_INSTRUMENTATION);
        }
    }
}
