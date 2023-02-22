package co.elastic.apm.plugin.spi;

import java.util.List;

public interface WebConfiguration {

    boolean isUsePathAsName();

    List<? extends WildcardMatcher> getIgnoreUrls();

    List<? extends WildcardMatcher> getUrlGroups();

    List<? extends WildcardMatcher> getCaptureContentTypes();

    List<? extends WildcardMatcher> getIgnoreUserAgents();
}
