package co.elastic.apm.agent.servlet;

public interface InstrumentationClassHelper {

    String SERVLET_API = "servlet-api";
    String SERVLET_API_DISPATCH = "servlet-api-dispatch";


    default String asyncContextClassName() {
        return "javax.servlet.AsyncContext";
    }

    default String servletClassName() {
        return "javax.servlet.Servlet";
    }

    default String servletRequestClassName() {
        return "javax.servlet.ServletRequest";
    }

    default String servletResponseClassName() {
        return "javax.servlet.ServletResponse";
    }

    default String servletApiAdviceClassName() {
        return "co.elastic.apm.agent.servlet.ServletApiAdvice";
    }

    default String servletConfigClassName() { return "javax.servlet.ServletConfig"; }

    void logServletVersion(Object servletConfig);
}
