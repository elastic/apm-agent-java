package co.elastic.apm.plugin.spi;

import java.util.Collection;

public interface StacktraceConfiguration {

    Collection<String> getApplicationPackages();
}
