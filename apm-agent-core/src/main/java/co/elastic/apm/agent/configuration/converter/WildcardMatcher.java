package co.elastic.apm.agent.configuration.converter;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class WildcardMatcher extends co.elastic.apm.agent.common.util.WildcardMatcher implements co.elastic.apm.plugin.spi.WildcardMatcher {

    private static final WildcardMatcher MATCH_ALL = new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.matchAll());
    private static final List<WildcardMatcher> MATCH_ALL_LIST = Collections.singletonList(MATCH_ALL);

    private final co.elastic.apm.agent.common.util.WildcardMatcher matcher;

    public WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher matcher) {
        this.matcher = matcher;
    }

    public static WildcardMatcher valueOf(String wildcardString) {
        return new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.valueOf(wildcardString));
    }

    public static WildcardMatcher caseSensitiveMatcher(String matcher) {
        return new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.caseSensitiveMatcher(matcher));
    }

    public static WildcardMatcher matchAll() {
        return MATCH_ALL;
    }

    public static List<WildcardMatcher> matchAllList() {
        return MATCH_ALL_LIST;
    }

    @Override
    public boolean matches(CharSequence s) {
        return matcher.matches(s);
    }

    @Override
    public boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart) {
        return matcher.matches(firstPart, secondPart);
    }

    @Override
    public String getMatcher() {
        return matcher.getMatcher();
    }
}
