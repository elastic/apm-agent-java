package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Transaction<T extends Transaction<T>> extends AbstractSpan<T> {

    String TYPE_REQUEST = "request";

    TransactionContext getContext();

    boolean isNoop();

    void ignoreTransaction();

    void addCustomContext(String key, String value);

    void addCustomContext(String key, Number value);

    void addCustomContext(String key, Boolean value);

    void setFrameworkName(@Nullable String frameworkName);

    void setUserFrameworkName(@Nullable String frameworkName);

    T captureException(@Nullable Throwable thrown);

    T withResult(@Nullable String result);

    T withResultIfUnset(@Nullable String result);

    String getType();

    void setFrameworkVersion(@Nullable String frameworkVersion);

    Faas getFaas();

    void setUser(String id, String email, String username, String domain);
}
