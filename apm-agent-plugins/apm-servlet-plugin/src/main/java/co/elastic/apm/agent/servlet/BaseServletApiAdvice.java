package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.determineServiceName;

public abstract class BaseServletApiAdvice<ServletRequest, HttpServletRequest, ServletContext> {

    private static final String FRAMEWORK_NAME = "Servlet API";
    static final String SPAN_TYPE = "servlet";
    static final String SPAN_SUBTYPE = "request-dispatcher";
    private static final ServletTransactionHelper servletTransactionHelper;

    static {
        servletTransactionHelper = new ServletTransactionHelper(GlobalTracer.requireTracerImpl());
    }

    private static final GlobalThreadLocal<Boolean> excluded = GlobalThreadLocal.get(BaseServletApiAdvice.class, "excluded");
    private static final GlobalThreadLocal<Object> servletPathTL = GlobalThreadLocal.get(BaseServletApiAdvice.class, "servletPath");
    private static final GlobalThreadLocal<Object> pathInfoTL = GlobalThreadLocal.get(BaseServletApiAdvice.class, "pathInfo");

    private static final List<String> requestExceptionAttributes = Arrays.asList("javax.servlet.error.exception", "jakarta.servlet.error.exception", "exception", "org.springframework.web.servlet.DispatcherServlet.EXCEPTION", "co.elastic.apm.exception");

    public static Object onServletEnter(Object servletRequest) {

        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        AbstractSpan<?> ret = null;
        // re-activate transactions for async requests
        final Transaction transactionAttr = getTransactionAttribute(servletRequest);
        if (tracer.currentTransaction() == null && transactionAttr != null) {
            return transactionAttr.activateInScope();
        }

        if (!tracer.isRunning() || !isHttpServletRequest(servletRequest)) {
            return null;
        }

        final HttpServletRequest httpServletRequest = getHttpServletRequest(servletRequest);
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);

        if (isRequestDispatcherType(servletRequest)) {
            if (Boolean.TRUE == excluded.get()) {
                return null;
            }

            ServletContext servletContext = getServletContext(servletRequest);
            if (servletContext != null) {
                ClassLoader servletCL = getClassloader(servletContext);
                // this makes sure service name discovery also works when attaching at runtime
                determineServiceName(getServletContextName(servletContext), servletCL, getContextPath(servletContext));
            }

            Transaction transaction = createAndActivateTransaction(httpServletRequest);

            if (transaction == null) {
                // if the httpServletRequest is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return null;
            }

            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && coreConfig.isCaptureHeaders()) {
                handleCookies(req, httpServletRequest);
                final Enumeration<String> headerNames = getHeaderNames(httpServletRequest);
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        final String headerName = headerNames.nextElement();
                        req.addHeader(headerName, getHeader(httpServletRequest, headerName));
                    }
                }
            }
            transaction.setFrameworkName(FRAMEWORK_NAME);

            servletTransactionHelper.fillRequestContext(transaction, getProtocol(httpServletRequest), getProtocol(httpServletRequest), isSecure(httpServletRequest),
                getScheme(httpServletRequest), getServerName(httpServletRequest), getServerPort(httpServletRequest), getRequestURI(httpServletRequest), getQueryString(httpServletRequest),
                getRemoteAddr(httpServletRequest), getHeader(httpServletRequest, "Content-Type"));

            ret = transaction;
        } else if (!isAsyncDispatcherType(servletRequest) &&
            !coreConfig.getDisabledInstrumentations().contains(Constants.SERVLET_API_DISPATCH)) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent != null) {
                Object servletPath = null;
                Object pathInfo = null;
                RequestDispatcherSpanType spanType = null;
                if (isForwardDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.FORWARD;
                    servletPath = getServletPath(httpServletRequest);
                    pathInfo = getPathInfo(httpServletRequest);
                } else if (isIncludeDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.INCLUDE;
                    servletPath = getIncludeServletPathAttribute(httpServletRequest);
                    pathInfo = getIncludePathInfoAttribute(httpServletRequest);
                } else if (isErrorDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.ERROR;
                    servletPath = getServletPath(httpServletRequest);
                }

                if (spanType != null && (areNotEqual(servletPathTL.get(), servletPath) || areNotEqual(pathInfoTL.get(), pathInfo))) {
                    ret = parent.createSpan()
                        .appendToName(spanType.getNamePrefix())
                        .withAction(spanType.getAction())
                        .withType(SPAN_TYPE)
                        .withSubtype(SPAN_SUBTYPE);

                    if (servletPath != null) {
                        ret.appendToName(servletPath.toString());
                        servletPathTL.set(servletPath);
                    }
                    if (pathInfo != null) {
                        ret.appendToName(pathInfo.toString());
                        pathInfoTL.set(pathInfo);
                    }
                    ret.activate();
                }
            }
        }
        return ret;
    }

    private static boolean areNotEqual(@Nullable Object first, @Nullable Object second) {
        if (first == null) {
            return second != null;
        } else {
            return !first.equals(second);
        }
    }
}
