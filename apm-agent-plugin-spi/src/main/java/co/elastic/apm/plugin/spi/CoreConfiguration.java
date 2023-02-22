package co.elastic.apm.plugin.spi;

import java.util.List;

public interface CoreConfiguration {
    boolean isEnablePublicApiAnnotationInheritance();

    boolean isCaptureHeaders();

    List<? extends WildcardMatcher> getSanitizeFieldNames();

    boolean isCaptureBody();

    boolean isInstrumentationEnabled(String instrumentationGroupName);
}
