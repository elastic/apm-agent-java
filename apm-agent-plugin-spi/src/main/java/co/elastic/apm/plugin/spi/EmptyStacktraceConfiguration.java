package co.elastic.apm.plugin.spi;

import java.util.Collection;
import java.util.Collections;

public class EmptyStacktraceConfiguration implements StacktraceConfiguration {

    public static final StacktraceConfiguration INSTANCE = new EmptyStacktraceConfiguration();

    private EmptyStacktraceConfiguration() {
    }

    @Override
    public Collection<String> getApplicationPackages() {
        return Collections.<String>emptyList();
    }
}
