package co.elastic.apm.spring.webmvc;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This {@link HandlerInterceptor} sets the {@link Transaction#name} to the handler responsible for this request.
 * <p>
 * If the handler is a {@link org.springframework.stereotype.Controller}, the {@link Transaction#name} is set to
 * <code>ControllerName#methodName</code>.
 * If it is a different kind of handler,
 * like a {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler},
 * the request name is set to the simple class name of the handler.
 * </p>
 */
public class ApmHandlerInterceptor implements HandlerInterceptor {

    private final ElasticApmTracer tracer;

    public ApmHandlerInterceptor() {
        this(ElasticApmTracer.get());
    }

    ApmHandlerInterceptor(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final Transaction transaction = tracer.currentTransaction();
        if (transaction != null) {
            setTransactionName(handler, transaction);
        }
        return true;
    }

    private void setTransactionName(Object handler, Transaction transaction) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = ((HandlerMethod) handler);
            transaction.getName().setLength(0);
            transaction.getName().append(handlerMethod.getBeanType().getSimpleName()).append('#').append(handlerMethod.getMethod().getName());
        } else {
            transaction.setName(handler.getClass().getSimpleName());
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // noop
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // noop
    }
}
