package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.servlet.ServletInstrumentation.SERVLET_API;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ServletContextServiceNameInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("javax.servlet.Servlet")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("init")
            .and(takesArgument(0, named("javax.servlet.ServletConfig")))
            .and(takesArguments(1));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ServletContextServiceNameAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    public static class ServletContextServiceNameAdvice {

        @Advice.OnMethodEnter
        public static void onServletInit(@Nullable @Advice.Argument(0) ServletConfig servletConfig) {
            if (tracer == null || servletConfig == null) {
                return;
            }
            ServletContext servletContext = servletConfig.getServletContext();
            if (servletContext == null) {
                return;
            }
            String serviceName = servletContext.getServletContextName();
            final String contextPath = servletContext.getContextPath();
            if (serviceName == null && contextPath != null && !contextPath.isEmpty()) {
                serviceName = contextPath;
            }
            if (serviceName != null) {
                tracer.overrideServiceNameForClassLoader(servletContext.getClassLoader(), serviceName);
            }
        }
    }
}
