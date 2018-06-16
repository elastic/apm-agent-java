package co.elastic.apm.servlet;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Enumeration;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Only the methods annotated with {@link Advice.OnMethodEnter} and {@link Advice.OnMethodExit} may contain references to
 * {@code javax.servlet}, as these are inlined into the matching methods.
 * The agent itself does not have access to the Servlet API classes, as they are loaded by a child class loader.
 * See https://github.com/raphw/byte-buddy/issues/465 for more information.
 */
public class AsyncInstrumentation extends ElasticApmInstrumentation {

    public static final String SERVLET_API_ASYNC_GROUP_NAME = "servlet-api-async";

    @Override
    public void init(ElasticApmTracer tracer) {
        StartAsyncAdvice.init(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(nameContains("Request"))
            .and(hasSuperType(named("javax.servlet.http.HttpServletRequest")));
    }

    /**
     * Matches
     * <ul>
     * <li>{@link HttpServletRequest#startAsync()}</li>
     * <li>{@link HttpServletRequest#startAsync(ServletRequest, ServletResponse)}</li>
     * </ul>
     *
     * @return
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic()
            .and(named("startAsync"))
            .and(returns(named("javax.servlet.AsyncContext")))
            .and(takesArguments(0)
                .or(
                    takesArgument(0, named("javax.servlet.ServletRequest"))
                        .and(takesArgument(1, named("javax.servletServletResponse")))
                )
            );
    }

    @Override
    public String getInstrumentationGroupName() {
        return SERVLET_API_ASYNC_GROUP_NAME;
    }

    @Override
    public Class<?> getAdviceClass() {
        return StartAsyncAdvice.class;
    }

    public static class StartAsyncAdvice {
        private static final String ASYNC_LISTENER_ADDED = ServletApiAdvice.class.getName() + ".asyncListenerAdded";
        @Nullable
        @VisibleForAdvice
        public static ServletTransactionHelper servletTransactionHelper;

        static void init(ElasticApmTracer tracer) {
            servletTransactionHelper = new ServletTransactionHelper(tracer);
        }

        @Advice.OnMethodExit
        private static void onExitStartAsync(@Advice.Return AsyncContext asyncContext) {
            final ServletRequest request = asyncContext.getRequest();
            if (servletTransactionHelper != null &&
                request.getAttribute(ServletApiAdvice.TRANSACTION_ATTRIBUTE) != null &&
                request.getAttribute(ASYNC_LISTENER_ADDED) == null) {
                // makes sure that the listener is only added once, even if the request is wrapped
                // which leads to multiple invocations of startAsync for the same underlying request
                request.setAttribute(ASYNC_LISTENER_ADDED, Boolean.TRUE);
                // we have to work with anonymous inner classes here (see class-level Javadoc)
                asyncContext.addListener(new AsyncListener() {
                    private volatile boolean complete = false;

                    @Override
                    public void onComplete(AsyncEvent event) {
                        if (!complete) {
                            endTransaction(event);
                            complete = true;
                        }
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        if (!complete) {
                            endTransaction(event);
                            complete = true;
                        }
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        if (!complete) {
                            endTransaction(event);
                            complete = true;
                        }
                    }

                    // taken from
                    // https://github.com/openzipkin/brave/blob/release-5.0.0/instrumentation/servlet/src/main/java/brave/servlet/ServletRuntime.java#L110
                    // cheers to @adriancole

                    /** If another async is created (ex via asyncContext.dispatch), this needs to be re-attached */
                    @Override
                    public void onStartAsync(AsyncEvent event) {
                        AsyncContext eventAsyncContext = event.getAsyncContext();
                        if (eventAsyncContext != null) eventAsyncContext.addListener(this);
                    }

                    // unfortunately, the duplication can't be avoided,
                    // because only the onExitServletService method may contain references to the servlet API
                    // (see class-level Javadoc)
                    private void endTransaction(AsyncEvent event) {
                        HttpServletRequest request = (HttpServletRequest) event.getSuppliedRequest();
                        HttpServletResponse response = (HttpServletResponse) event.getSuppliedResponse();
                        Transaction transaction = (Transaction) request.getAttribute(ServletApiAdvice.TRANSACTION_ATTRIBUTE);
                        final Request req = transaction.getContext().getRequest();
                        if (request.getCookies() != null) {
                            for (Cookie cookie : request.getCookies()) {
                                req.addCookie(cookie.getName(), cookie.getValue());
                            }
                        }
                        final Enumeration headerNames = request.getHeaderNames();
                        while (headerNames.hasMoreElements()) {
                            final String headerName = (String) headerNames.nextElement();
                            req.addHeader(headerName, request.getHeader(headerName));
                        }
                        final Response resp = transaction.getContext().getResponse();
                        for (String headerName : response.getHeaderNames()) {
                            resp.addHeader(headerName, response.getHeader(headerName));
                        }
                        final Principal userPrincipal = request.getUserPrincipal();
                        servletTransactionHelper.onAfter(transaction, event.getThrowable(), userPrincipal != null ? userPrincipal.getName() : null,
                            request.getProtocol(), request.getMethod(), request.isSecure(), request.getScheme(), request.getServerName(),
                            request.getServerPort(), request.getRequestURI(), request.getQueryString(), request.getParameterMap(),
                            request.getRemoteAddr(), request.getRequestURL(), response.isCommitted(), response.getStatus());
                    }
                });
            }

        }
    }
}
