package co.elastic.apm.agent.opentelemetry.context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class ElasticOTelContext implements Context {
    private final Map<ContextKey<?>, Object> entries;

    public ElasticOTelContext() {
        this(new HashMap<>(4));
    }

    private ElasticOTelContext(Map<ContextKey<?>, Object> entries) {
        this.entries = entries;
    }

    @Nullable
    @Override
    public <V> V get(ContextKey<V> key) {
        return (V) entries.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
        Map<ContextKey<?>, Object> copy = new HashMap<>(entries);
        copy.put(k1, v1);
        return new ElasticOTelContext(copy);
    }
}
