package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.sdk.state.GlobalVariables;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class CommonServletVersionInstrumentation extends AbstractServletInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(CommonServletVersionInstrumentation.class);
    private static final AtomicBoolean alreadyLogged = GlobalVariables.get(CommonServletVersionInstrumentation.class, "alreadyLogged", new AtomicBoolean(false));

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(servletVersionTypeMatcherClassName())));
    }

    public abstract String servletVersionTypeMatcherClassName();


    /**
     * Instruments {@link javax.servlet.Servlet#init(ServletConfig)}
     */
    public static abstract class Init extends CommonServletVersionInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("init")
                .and(takesArgument(0, named(initMethodArgumentClassName())));
        }

        abstract String initMethodArgumentClassName();
    }

    public static abstract class Service extends CommonServletVersionInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            String[] classNames = getServiceMethodArgumentClassNames();
            return named("service")
                .and(takesArgument(0, named(classNames[0])))
                .and(takesArgument(1, named(classNames[1])));
        }

        abstract String[] getServiceMethodArgumentClassNames();
    }

    public static void logServletVersion(Supplier<Object[]> infoFetch) {
        if (alreadyLogged.get()) {
            return;
        }
        alreadyLogged.set(true);

        int majorVersion = -1;
        int minorVersion = -1;
        String serverInfo = null;
        Object[] infoFromServletContext = infoFetch.get();
        if (infoFromServletContext != null && infoFromServletContext.length > 2) {
            if (infoFromServletContext[0] != null) {
                majorVersion = (int) infoFromServletContext[0];
            }
            if (infoFromServletContext[1] != null) {
                minorVersion = (int) infoFromServletContext[1];
            }
            if (infoFromServletContext[2] instanceof String) {
                serverInfo = (String) infoFromServletContext[2];
            }
        }
        logger.info("Servlet container info = {}", serverInfo);
        if (majorVersion < 3) {
            logger.warn("Unsupported servlet version detected: {}.{}, no Servlet transaction will be created", majorVersion, minorVersion);
        }
    }

}
