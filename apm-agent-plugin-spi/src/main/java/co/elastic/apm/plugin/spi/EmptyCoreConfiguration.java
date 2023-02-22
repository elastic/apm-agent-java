package co.elastic.apm.plugin.spi;

import java.util.Collections;
import java.util.List;

public class EmptyCoreConfiguration implements CoreConfiguration {

    public static final CoreConfiguration INSTANCE = new EmptyCoreConfiguration();

    private EmptyCoreConfiguration() {
    }

    @Override
    public boolean isEnablePublicApiAnnotationInheritance() {
        return false;
    }

    @Override
    public boolean isCaptureHeaders() {
        return false;
    }

    @Override
    public List<? extends WildcardMatcher> getSanitizeFieldNames() {
        return Collections.<WildcardMatcher>emptyList();
    }

    @Override
    public boolean isCaptureBody() {
        return false;
    }

    @Override
    public boolean isInstrumentationEnabled(String instrumentationGroupName) {
        return false;
    }
}
