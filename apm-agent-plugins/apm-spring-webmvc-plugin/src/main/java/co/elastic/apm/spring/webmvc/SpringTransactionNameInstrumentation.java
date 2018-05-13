package co.elastic.apm.spring.webmvc;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * This instrumentation sets the {@link Transaction#name} according to the handler responsible for this request.
 * <p>
 * If the handler is a {@link org.springframework.stereotype.Controller}, the {@link Transaction#name} is set to
 * {@code ControllerName#methodName}.
 * If it is a different kind of handler,
 * like a {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler},
 * the request name is set to the simple class name of the handler.
 * </p>
 * <p>
 * Supports Spring MVC 3.x-5.x
 * </p>
 */
public class SpringTransactionNameInstrumentation extends ElasticApmInstrumentation {

    @Override
    public void init(ElasticApmTracer tracer) {
        HandlerAdapterAdvice.tracer = tracer;
    }

    /**
     * Instrumenting well defined interfaces like {@link org.springframework.web.servlet.HandlerAdapter}
     * is preferred over instrumenting private methods like
     * {@link org.springframework.web.servlet.DispatcherServlet#getHandler(javax.servlet.http.HttpServletRequest)},
     * as interfaces should be more stable.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(nameStartsWith("org.springframework.web.servlet"))
            .and(hasSuperType(named("org.springframework.web.servlet.HandlerAdapter")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(returns(named("org.springframework.web.servlet.ModelAndView")))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, Object.class));
    }

    @Override
    public Class<?> getAdviceClass() {
        return HandlerAdapterAdvice.class;
    }

    @VisibleForAdvice
    public static class HandlerAdapterAdvice {
        @Nullable
        @VisibleForAdvice
        public static ElasticApmTracer tracer;

        @Advice.OnMethodEnter
        static void setTransactionName(@Advice.Argument(2) Object handler) {
            if (tracer != null) {
                final Transaction transaction = tracer.currentTransaction();
                if (transaction != null) {
                    final String className;
                    final String methodName;
                    if (handler instanceof HandlerMethod) {
                        HandlerMethod handlerMethod = ((HandlerMethod) handler);
                        className = handlerMethod.getBeanType().getSimpleName();
                        methodName = handlerMethod.getMethod().getName();
                    } else {
                        className = handler.getClass().getSimpleName();
                        methodName = null;
                    }
                    setName(transaction, className, methodName);
                }
            }
        }

        @VisibleForAdvice
        public static void setName(Transaction transaction, String className, @Nullable String methodName) {
            final StringBuilder name = transaction.getName();
            name.setLength(0);
            name.append(className);
            if (methodName != null) {
                name.append('#').append(methodName);
            }
        }
    }
}
