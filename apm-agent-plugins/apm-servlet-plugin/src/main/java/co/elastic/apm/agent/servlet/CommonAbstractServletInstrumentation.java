package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.servlet.InstrumentationClassHelper.SERVLET_API;

public abstract class CommonAbstractServletInstrumentation extends TracerAwareInstrumentation {

    protected final static JavaxInstrumentationClassHelperImpl javaxClassHelper;
    protected final static JakartaInstrumentationClassHelperImpl jakartaClassHelper;

    protected final static InstrumentationClassHelper instrumentationClassHelper;

    static {
        javaxClassHelper = new JavaxInstrumentationClassHelperImpl();
        jakartaClassHelper = new JakartaInstrumentationClassHelperImpl();
    }


    public CommonAbstractServletInstrumentation(InstrumentationClassHelper helper) {
        this.instrumentationClassHelper = helper;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // this class has been introduced in servlet spec 3.0
        // choice of class name to use for this test does not work as expected across all application servers
        // for example, 'javax.servlet.annotation.WebServlet' annotation is not working as expected on Payara
        return CustomElementMatchers.classLoaderCanLoadClass(instrumentationClassHelper.asyncContextClassName());
    }
}
