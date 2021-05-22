package co.elastic.apm.agent.servlet;

public class JavaxServletHelper implements CommonServletHelper{
    @Override
    public String getClassloaderMatcherClassName() {
        return "javax.servlet.AsyncContext";
    }
}
