package co.elastic.apm.agent.bci.methodmatching.configuration;

import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import org.stagemonitor.configuration.converter.ValueConverter;

public enum MethodMatcherValueConverter implements ValueConverter<MethodMatcher> {
    INSTANCE;

    @Override
    public MethodMatcher convert(String methodMatcher) throws IllegalArgumentException {
        return MethodMatcher.of(methodMatcher);
    }

    @Override
    public String toString(MethodMatcher methodMatcher) {
        return methodMatcher.toString();
    }

    @Override
    public String toSafeString(MethodMatcher value) {
        return toString(value);
    }
}
