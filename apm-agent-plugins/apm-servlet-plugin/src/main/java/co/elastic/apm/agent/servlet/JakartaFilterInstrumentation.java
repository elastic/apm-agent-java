package co.elastic.apm.agent.servlet;

/**
 * Instruments {@link jakarta.servlet.Filter}s to create transactions.
 */
public class JakartaFilterInstrumentation extends CommonFilterInstrumentation{
    @Override
    public String getFilterClassName() {
        return "jakarta.servlet.Filter";
    }

    @Override
    public String[] getServletMethodArgumentNames() {
        return new String[]{"jakarta.servlet.ServletRequest", "jakarta.servlet.ServletResponse"};
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JakartaServletApiAdvice";
    }
}
