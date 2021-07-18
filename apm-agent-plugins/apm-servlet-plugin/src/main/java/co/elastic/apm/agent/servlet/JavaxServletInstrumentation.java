package co.elastic.apm.agent.servlet;

public class JavaxServletInstrumentation extends ServletInstrumentation {
    @Override
    public String getServletClassName() {
        return "javax.servlet.Servlet";
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
