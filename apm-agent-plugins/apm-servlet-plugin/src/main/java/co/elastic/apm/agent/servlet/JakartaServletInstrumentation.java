package co.elastic.apm.agent.servlet;

public class JakartaServletInstrumentation extends ServletInstrumentation {
    @Override
    public String getServletClassName() {
        return "jakarta.servlet.Servlet";
    }

    @Override
    public String[] getServletMethodArgumentNames() {
        return new String[]{"jakarta.servlet.ServletRequest", "jakarta.servlet.ServletResponse"};
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
