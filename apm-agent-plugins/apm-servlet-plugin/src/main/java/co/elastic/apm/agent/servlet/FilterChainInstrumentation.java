package co.elastic.apm.agent.servlet;

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link javax.servlet.FilterChain}s to create transactions.
 */
public abstract class FilterChainInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Chain");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(filterChainTypeMatcherClassName())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doFilter")
            .and(takesArgument(0, named(doFilterFirstArgumentClassName())))
            .and(takesArgument(1, named(doFilterSecondArgumentClassName())));
    }

    abstract String filterChainTypeMatcherClassName();

    abstract String doFilterFirstArgumentClassName();

    abstract String doFilterSecondArgumentClassName();
}
