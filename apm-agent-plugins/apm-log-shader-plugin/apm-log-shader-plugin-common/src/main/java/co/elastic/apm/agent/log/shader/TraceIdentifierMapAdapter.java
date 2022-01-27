package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

public class TraceIdentifierMapAdapter extends AbstractMap<String, String> {

    private static final TraceIdentifierMapAdapter INSTANCE = new TraceIdentifierMapAdapter();

    private static final Set<Entry<String, String>> ENTRY_SET = new TraceIdentifierEntrySet();
    private static final List<String> ALL_KEYS = Arrays.asList("trace.id", "transaction.id", "span.id");
    private static final Tracer tracer = GlobalTracer.get();
    private static final List<Entry<String, String>> ENTRIES = Arrays.asList(
        new LazyEntry("trace.id", new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                Transaction transaction = tracer.currentTransaction();
                if (transaction == null) {
                    return null;
                }
                return transaction.getTraceContext().getTraceId().toString();
            }
        }),
        new LazyEntry("transaction.id", new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                Transaction transaction = tracer.currentTransaction();
                if (transaction == null) {
                    return null;
                }
                return transaction.getTraceContext().getId().toString();
            }
        }),
        new LazyEntry("span.id", new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                Span span = tracer.getActiveSpan();
                if (span == null) {
                    return null;
                }
                return span.getTraceContext().getId().toString();
            }
        })
    );

    public static Map<String, String> get() {
        return INSTANCE;
    }

    private TraceIdentifierMapAdapter() {
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return ENTRY_SET;
    }

    public Iterable<String> allKeys() {
        return ALL_KEYS;
    }

    private static class TraceIdentifierEntrySet extends AbstractSet<Entry<String, String>> {

        @Override
        public int size() {
            int size = 0;
            for (Entry<String, String> ignored : this) {
                size++;
            }
            return size;
        }

        @Override
        public Iterator<Entry<String, String>> iterator() {
            return new Iterator<Entry<String, String>>() {
                private int i = 0;
                @Nullable
                private Entry<String, String> next = findNext();

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Entry<String, String> next() {
                    if (next != null) {
                        try {
                            return next;
                        } finally {
                            next = findNext();
                        }
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Nullable
                private Entry<String, String> findNext() {
                    Entry<String, String> next = null;
                    while (next == null && i < ENTRIES.size()) {
                        next = ENTRIES.get(i++);
                        if (next.getValue() == null) {
                            next = null;
                        }
                    }
                    return next;
                }
            };
        }

    }

    private static class LazyEntry implements Entry<String, String> {
        private final String key;
        private final Callable<String> valueSupplier;

        private LazyEntry(String key, Callable<String> valueSupplier) {
            this.key = key;
            this.valueSupplier = valueSupplier;
        }

        @Override
        public String getKey() {
            return this.key;
        }

        @Override
        public String getValue() {
            try {
                return this.valueSupplier.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }
}
