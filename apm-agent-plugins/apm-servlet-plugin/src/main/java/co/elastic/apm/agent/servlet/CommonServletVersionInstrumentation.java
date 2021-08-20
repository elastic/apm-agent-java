package co.elastic.apm.agent.servlet;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class CommonServletVersionInstrumentation extends CommonAbstractServletInstrumentation {

    public CommonServletVersionInstrumentation(InstrumentationClassHelper helper) {
        super(helper);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }


    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(instrumentationClassHelper.servletClassName())));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return any();
    }

    /**
     * Instruments {@link javax.servlet.Servlet#init(ServletConfig)}
     */
    public static class Init extends CommonServletVersionInstrumentation {



        public Init(InstrumentationClassHelper helper, Supplier logSupplier) {
            super(helper);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("init")
                .and(takesArgument(0, named(instrumentationClassHelper.servletConfigClassName())));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        public static void onEnter(@Advice.Argument(0) @Nullable Object servletConfig) {
            inslogServletVersion(servletConfig);
        }
    }

    /**
     * Instruments {@link javax.servlet.Servlet#service(ServletRequest, ServletResponse)}
     */
    public static class Service extends CommonServletVersionInstrumentation {

        public Service(InstrumentationClassHelper helper) {
            super(helper);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("service")
                .and(takesArgument(0, named(instrumentationClassHelper.servletRequestClassName())))
                .and(takesArgument(1, named(instrumentationClassHelper.servletResponseClassName())));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Object servlet) {
            logServletVersion(servlet.getServletConfig());
        }
    }
}
