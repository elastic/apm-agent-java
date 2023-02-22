package co.elastic.apm.plugin.spi;

import java.util.Collections;
import java.util.List;

public class EmptyWebConfiguration implements WebConfiguration {

    public static final WebConfiguration INSTANCE = new EmptyWebConfiguration();

    private EmptyWebConfiguration() {
    }

    @Override
    public boolean isUsePathAsName() {
        return false;
    }

    @Override
    public List<? extends WildcardMatcher> getIgnoreUrls() {
        return Collections.<WildcardMatcher>emptyList();
    }

    @Override
    public List<? extends WildcardMatcher> getUrlGroups() {
        return Collections.<WildcardMatcher>emptyList();
    }

    @Override
    public List<? extends WildcardMatcher> getCaptureContentTypes() {
        return Collections.<WildcardMatcher>emptyList();
    }

    @Override
    public List<? extends WildcardMatcher> getIgnoreUserAgents() {
        return Collections.<WildcardMatcher>emptyList();
    }
}
