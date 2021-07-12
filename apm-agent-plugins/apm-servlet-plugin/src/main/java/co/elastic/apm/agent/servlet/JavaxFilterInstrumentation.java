package co.elastic.apm.agent.servlet;

/**
 * Instruments {@link javax.servlet.Filter}s to create transactions.
 */
public class JavaxFilterInstrumentation extends CommonFilterInstrumentation{
    @Override
    public String getFilterClassName() {
        return "javax.servlet.Filter";
    }

    @Override
    public String[] getServletMethodArgumentNames() {
        return new String[]{"javax.servlet.ServletRequest", "javax.servlet.ServletResponse"};
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JavaxServletApiAdvice";
    }
}
