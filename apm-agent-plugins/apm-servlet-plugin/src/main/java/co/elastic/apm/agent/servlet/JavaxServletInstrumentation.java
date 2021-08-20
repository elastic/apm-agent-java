package co.elastic.apm.agent.servlet;

public class JavaxServletInstrumentation extends CommonServletInstrumentation{
    public JavaxServletInstrumentation() {
        super(javaxClassHelper);
    }
}
