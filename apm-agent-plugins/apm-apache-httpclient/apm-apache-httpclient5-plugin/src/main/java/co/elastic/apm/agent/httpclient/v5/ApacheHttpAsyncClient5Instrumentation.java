package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.common.AbstractApacheHttpAsyncClientAdvice;
import co.elastic.apm.agent.httpclient.v5.helper.ApacheHttpAsyncClientHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpAsyncClient5Instrumentation extends BaseApacheHttpClient5Instrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v5.ApacheHttpAsyncClient5Instrumentation$ApacheHttpAsyncClient5Advice";
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.hc.client5.http.async.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpAsyncClient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.hc.client5.http.async.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.hc.core5.http.nio.AsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.nio.AsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.hc.core5.concurrent.FutureCallback")));
    }

    public static class ApacheHttpAsyncClient5Advice extends AbstractApacheHttpAsyncClientAdvice {

        private static ApacheHttpAsyncClientHelper asyncHelper = new ApacheHttpAsyncClientHelper();

        @Advice.AssignReturned.ToArguments({
            @Advice.AssignReturned.ToArguments.ToArgument(index = 0, value = 0, typing = Assigner.Typing.DYNAMIC),
            @Advice.AssignReturned.ToArguments.ToArgument(index = 1, value = 3, typing = Assigner.Typing.DYNAMIC)
        })
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeExecute(@Advice.Argument(value = 0) AsyncRequestProducer asyncRequestProducer,
                                               @Advice.Argument(value = 2) HttpContext context,
                                               @Advice.Argument(value = 3) FutureCallback<?> futureCallback) {
            return startSpan(asyncHelper, asyncRequestProducer, context, futureCallback);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object[] enter,
                                          @Advice.Thrown @Nullable Throwable t) {
            endSpan(enter, t);
        }
    }
}
