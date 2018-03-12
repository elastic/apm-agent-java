package co.elastic.apm.configuration;

import org.stagemonitor.configuration.source.ConfigurationSource;

import java.io.IOException;

public class PrefixingConfigurationSourceWrapper implements ConfigurationSource {
    private final ConfigurationSource delegate;
    private final String prefix;

    public PrefixingConfigurationSourceWrapper(ConfigurationSource delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix;
    }

    @Override
    public String getValue(String key) {
        return delegate.getValue(prefix + key);
    }

    @Override
    public void reload() throws IOException {
        delegate.reload();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isSavingPossible() {
        return delegate.isSavingPossible();
    }

    @Override
    public boolean isSavingPersistent() {
        return delegate.isSavingPersistent();
    }

    @Override
    public void save(String key, String value) throws IOException {
        delegate.save(prefix + key, value);
    }
}
