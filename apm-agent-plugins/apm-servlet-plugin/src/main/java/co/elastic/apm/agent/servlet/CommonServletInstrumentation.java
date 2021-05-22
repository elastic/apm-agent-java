package co.elastic.apm.agent.servlet;

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link CommonFilterChainInstrumentation},
 * it does not record the transaction again.
 * But if there is no filter registered for a servlet,
 * this makes sure to record a transaction in that case.
 * </p>
 */
public abstract class CommonServletInstrumentation extends AbstractServletInstrumentation {

    static final String SERVLET_API = "servlet-api";
    static final String SERVLET_API_DISPATCH = "servlet-api-dispatch";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(SERVLET_API, SERVLET_API_DISPATCH);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(getServletClassName())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        String[] classNames = getServletMethodArgumentNames();
        return named("service")
            .and(takesArgument(0, named(classNames[0])))
            .and(takesArgument(1, named(classNames[1])));
    }

    public abstract String getServletClassName();

    public abstract String[] getServletMethodArgumentNames();

}
