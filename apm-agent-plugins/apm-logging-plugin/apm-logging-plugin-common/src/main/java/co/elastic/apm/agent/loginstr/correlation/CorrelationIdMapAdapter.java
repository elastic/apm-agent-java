/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.loginstr.correlation;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.tracer.Tracer;

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

import static co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper.ERROR_ID_MDC_KEY;
import static co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY;
import static co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY;

public class CorrelationIdMapAdapter extends AbstractMap<String, String> {

    private static final CorrelationIdMapAdapter INSTANCE = new CorrelationIdMapAdapter();
    private static final Set<Entry<String, String>> ENTRY_SET = new TraceIdentifierEntrySet();
    private static final List<String> ALL_KEYS = Arrays.asList(TRACE_ID_MDC_KEY, TRANSACTION_ID_MDC_KEY, ERROR_ID_MDC_KEY);
    private static final Tracer tracer = GlobalTracer.get();
    private static final List<Entry<String, String>> ENTRIES = Arrays.<Entry<String, String>>asList(
        new LazyEntry(TRACE_ID_MDC_KEY, new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                AbstractSpan<?> activeSpan = tracer.getActive();
                if (activeSpan == null) {
                    return null;
                }
                return activeSpan.getTraceContext().getTraceId().toString();
            }
        }),
        new LazyEntry(TRANSACTION_ID_MDC_KEY, new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                AbstractSpan<?> activeSpan = tracer.getActive();
                if (activeSpan == null) {
                    return null;
                }
                return activeSpan.getTraceContext().getTransactionId().toString();
            }
        }),
        new LazyEntry(ERROR_ID_MDC_KEY, new Callable<String>() {
            @Override
            @Nullable
            public String call() {
                ErrorCapture error = ErrorCapture.getActive();
                if (error == null) {
                    return null;
                }
                return error.getTraceContext().getId().toString();
            }
        })
    );

    public static Map<String, String> get() {
        return INSTANCE;
    }

    public static Iterable<String> allKeys() {
        return ALL_KEYS;
    }

    private CorrelationIdMapAdapter() {
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return ENTRY_SET;
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
