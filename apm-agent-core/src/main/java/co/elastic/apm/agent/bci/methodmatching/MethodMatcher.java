package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;

public class MethodMatcher {

    private static final String MODIFIER = "(public|private|protected|\\*)";
    private static final String CLASS_NAME = "([a-zA-Z\\d_$.\\*]+)";
    private static final String METHOD_NAME = "([a-zA-Z\\d_$\\*]+)";
    private static final String PARAM = "([a-zA-Z\\d_$.\\[\\]\\*]+)";
    private static final String PARAMS = PARAM + "(,\\s*" + PARAM + ")*";
    private static final Pattern METHOD_MATCHER_PATTERN = Pattern.compile("^(" + MODIFIER + "\\s+)?" + CLASS_NAME + "#" + METHOD_NAME + "(\\((" + PARAMS + ")*\\))?$");

    private final String stringRepresentation;
    @Nullable
    private final Integer modifier;
    private final WildcardMatcher classMatcher;
    private final WildcardMatcher methodMatcher;
    @Nullable
    private final List<WildcardMatcher> argumentMatchers;

    private MethodMatcher(String stringRepresentation, @Nullable Integer modifier, WildcardMatcher classMatcher, WildcardMatcher methodMatcher, @Nullable List<WildcardMatcher> argumentMatchers) {
        this.stringRepresentation = stringRepresentation;
        this.modifier = modifier;
        this.classMatcher = classMatcher;
        this.methodMatcher = methodMatcher;
        this.argumentMatchers = argumentMatchers;
    }

    public static MethodMatcher of(String methodMatcher) {
        final Matcher matcher = METHOD_MATCHER_PATTERN.matcher(methodMatcher);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'" + methodMatcher + "'" + " is not a valid method matcher");
        }

        return new MethodMatcher(methodMatcher, getModifier(matcher.group(2)), caseSensitiveMatcher(matcher.group(3)), caseSensitiveMatcher(matcher.group(4)),
            getArgumentMatchers(matcher.group(5)));
    }

    @Nullable
    private static Integer getModifier(@Nullable String modifier) {
        if (modifier == null) {
            return null;
        }
        switch (modifier) {
            case "public":
                return Modifier.PUBLIC;
            case "private":
                return Modifier.PRIVATE;
            case "protected":
                return Modifier.PROTECTED;
            default:
                return null;
        }
    }

    @Nullable
    private static List<WildcardMatcher> getArgumentMatchers(@Nullable String arguments) {
        if (arguments == null) {
            return null;
        }
        // remove parenthesis
        arguments = arguments.substring(1, arguments.length() - 1);
        final String[] splitArguments = StringUtils.split(arguments, ',');
        List<WildcardMatcher> matchers = new ArrayList<>(splitArguments.length);
        for (String argument : splitArguments) {
            matchers.add(caseSensitiveMatcher(argument.trim()));
        }
        return matchers;
    }

    public WildcardMatcher getClassMatcher() {
        return classMatcher;
    }

    @Nullable
    public Integer getModifier() {
        return modifier;
    }

    public WildcardMatcher getMethodMatcher() {
        return methodMatcher;
    }

    @Nullable
    public List<WildcardMatcher> getArgumentMatchers() {
        return argumentMatchers;
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }
}
