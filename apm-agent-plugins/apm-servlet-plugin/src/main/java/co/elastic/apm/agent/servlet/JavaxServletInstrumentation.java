package co.elastic.apm.agent.servlet;

public class JavaxServletInstrumentation extends ServletInstrumentation {
    @Override
    public String getServletClassName() {
        return "javax.servlet.Servlet";
    }

    @Override
    public String[] getServletMethodArgumentNames() {
        return new String[]{"javax.servlet.ServletRequest", "javax.servlet.ServletResponse"};
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
