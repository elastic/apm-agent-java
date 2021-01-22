package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import org.stagemonitor.util.StringUtils;

import java.util.List;

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
        // Lazily parses tracestate header.
        // Our internal TraceState class doesn't parse the raw tracestate header
        // as we currently don't have a use case where the agent needs to read the tracestate.
        TraceStateBuilder builder = TraceState.builder();
        List<String> tracestate = traceContext.getTraceState().getTracestate();
        for (int i = 0, size = tracestate.size(); i < size; i++) {
            for (String vendorEntry : StringUtils.split(tracestate.get(i), ',')) {
                String[] keyValue = StringUtils.split(vendorEntry, '=');
                if (keyValue.length == 2) {
                    builder.set(keyValue[0], keyValue[1]);
                }
            }
        }
        return builder.build();
    }

    @Override
    public boolean isRemote() {
        // the elastic agent doesn't create a TraceContext for remote parents
        // instead, it directly creates an entry child span given the request headers
        return false;
    }

}
