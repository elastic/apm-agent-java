package co.elastic.apm.agent.servlet;

public class JakartaFilterChainInstrumentation extends FilterChainInstrumentation {
    @Override
    String filterChainTypeMatcherClassName() {
        return "jakarta.servlet.FilterChain";
    }

    @Override
    String doFilterFirstArgumentClassName() {
        return "jakarta.servlet.ServletRequest";
    }

    @Override
    String doFilterSecondArgumentClassName() {
        return "jakarta.servlet.ServletResponse";
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JakartaServletApiAdvice";
    }

    @Override
    public String rootClassNameThatClassloaderCanLoad() {
        return "jakarta.servlet.AsyncContext";
    }
}
