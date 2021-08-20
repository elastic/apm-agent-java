package co.elastic.apm.agent.servlet;

public class JakartaServletInstrumentation extends CommonServletInstrumentation{
    public JakartaServletInstrumentation() {
        super(jakartaClassHelper);
    }
}
