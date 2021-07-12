package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;

import co.elastic.apm.agent.impl.GlobalTracer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

public class JakartaServletTransactionCreationHelper extends CommonServletTransactionCreationHelper<HttpServletRequest, ServletContext> {
    public JakartaServletTransactionCreationHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    protected String getServletPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getServletPath();
    }

    @Override
    protected String getPathInfo(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo();
    }

    @Override
    protected String getHeader(HttpServletRequest httpServletRequest, String headerName) {
        return httpServletRequest.getHeader(headerName);
    }

    @Override
    protected ServletContext getServletContext(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getServletContext();
    }

    @Override
    protected ClassLoader getClassLoader(ServletContext servletContext) {
        return servletContext.getClassLoader();
    }

    @Override
    protected CommonServletRequestHeaderGetter getRequestHeaderGetter() {
        return JakartaServletRequestHeaderGetter.getInstance();
    }
}
