package co.elastic.apm.agent.servlet;

/**
 * Instruments {@link javax.servlet.Filter}s to create transactions.
 */
public class JavaxFilterInstrumentation extends FilterInstrumentation {
    @Override
    public String getFilterClassName() {
        return "javax.servlet.Filter";
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
