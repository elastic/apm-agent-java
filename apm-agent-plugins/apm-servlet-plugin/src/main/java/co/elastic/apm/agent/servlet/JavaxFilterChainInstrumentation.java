package co.elastic.apm.agent.servlet;

public class JavaxFilterChainInstrumentation extends FilterChainInstrumentation {
    @Override
    String filterChainTypeMatcherClassName() {
        return "javax.servlet.FilterChain";
    }

    @Override
    String doFilterFirstArgumentClassName() {
        return "javax.servlet.ServletRequest";
    }

    @Override
    String doFilterSecondArgumentClassName() {
        return "javax.servlet.ServletResponse";
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JavaxServletApiAdvice";
    }

    @Override
    public String rootClassNameThatClassloaderCanLoad() {
        return "javax.servlet.AsyncContext";
    }
}
