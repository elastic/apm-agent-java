package co.elastic.apm.agent.servlet;

public class JakartaInstrumentationClassHelperImpl implements InstrumentationClassHelper {
    @Override
    public String asyncContextClassName() {
        return "jakarta.servlet.AsyncContext";
    }

    @Override
    public String servletClassName() {
        return "jakarta.servlet.Servlet";
    }

    @Override
    public String servletRequestClassName() {
        return "jakarta.servlet.ServletRequest";
    }

    @Override
    public String servletResponseClassName() {
        return "jakarta.servlet.ServletResponse";
    }

    @Override
    public String servletApiAdviceClassName() {
        return "co.elastic.apm.agent.servlet.ServletApiAdvice";
    }
}
