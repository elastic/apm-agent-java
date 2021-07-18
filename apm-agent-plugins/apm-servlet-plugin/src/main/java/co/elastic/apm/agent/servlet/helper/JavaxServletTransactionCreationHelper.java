package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

public class JavaxServletTransactionCreationHelper extends ServletTransactionCreationHelper<HttpServletRequest, ServletContext> {

    public JavaxServletTransactionCreationHelper(ElasticApmTracer tracer) {
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
        return JavaxServletRequestHeaderGetter.getInstance();
    }
}
