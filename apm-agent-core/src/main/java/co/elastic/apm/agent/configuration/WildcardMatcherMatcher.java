package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.configuration.Matcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WildcardMatcherMatcher extends Matcher {

    private final WildcardMatcher delegate;

    public WildcardMatcherMatcher(WildcardMatcher delegate) {
        this.delegate = delegate;
    }

    public static List<Matcher> wrap(Collection<? extends WildcardMatcher> wildcardMatchers) {
        List<Matcher> matchers = new ArrayList<>(wildcardMatchers.size());
        for (WildcardMatcher wildcardMatcher : wildcardMatchers) {
            matchers.add(new WildcardMatcherMatcher(wildcardMatcher));
        }
        return matchers;
    }

    @Override
    public boolean matches(CharSequence s) {
        return delegate.matches(s);
    }

    @Override
    public boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart) {
        return delegate.matches(firstPart, secondPart);
    }
}
