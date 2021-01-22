package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ElasticOTelSpanContext implements SpanContext {
    private final TraceContext traceContext;

    public ElasticOTelSpanContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public String getTraceIdAsHexString() {
        return traceContext.getTraceId().toString();
    }

    @Override
    public String getSpanIdAsHexString() {
        return traceContext.getId().toString();
    }

    @Override
    public byte getTraceFlags() {
        return traceContext.getFlags();
    }

    @Override
    public TraceState getTraceState() {
        return new ElasticOtelTraceState(traceContext.getTraceState());
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    private static class ElasticOtelTraceState implements TraceState {
        private final co.elastic.apm.agent.impl.transaction.TraceState traceState;

        public ElasticOtelTraceState(co.elastic.apm.agent.impl.transaction.TraceState traceState) {
            this.traceState = traceState;
        }

        @Nullable
        @Override
        public String get(String key) {
            List<String> tracestate = traceState.getTracestate();
            for (String ts : tracestate) {
                if (ts.startsWith(key + "=")) {
                    return ts.substring(key.length() + 1);
                }
            }
            return null;
        }

        @Override
        public int size() {
            return traceState.getTracestate().size();
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public void forEach(BiConsumer<String, String> consumer) {
            for (String s : traceState.getTracestate()) {
                consumer.accept(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1));
            }
        }

        @Override
        public Map<String, String> asMap() {
            HashMap<String, String> map = new HashMap<>();
            forEach(map::put);
            return map;
        }

        @Override
        public TraceStateBuilder toBuilder() {
            return new ElasticOTelTraceStateBuilder(traceState);
        }

        private static class ElasticOTelTraceStateBuilder implements TraceStateBuilder {
            private final co.elastic.apm.agent.impl.transaction.TraceState builder;

            public ElasticOTelTraceStateBuilder(co.elastic.apm.agent.impl.transaction.TraceState traceState) {
                this.builder = new co.elastic.apm.agent.impl.transaction.TraceState();
                this.builder.copyFrom(traceState);
            }

            @Override
            public TraceStateBuilder set(String key, String value) {
                builder.getTracestate().add(key + "=" + value);
                return this;
            }

            @Override
            public TraceStateBuilder remove(String key) {
                builder.getTracestate().removeIf(ts -> ts.startsWith(key + "="));
                return this;
            }

            @Override
            public TraceState build() {
                return new ElasticOtelTraceState(builder);
            }
        }
    }
}
