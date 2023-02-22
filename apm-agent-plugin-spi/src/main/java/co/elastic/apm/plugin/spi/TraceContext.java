package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface TraceContext {

    String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    String W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME = "traceparent";
    String TRACESTATE_HEADER_NAME = "tracestate";
    public static final int SERIALIZED_LENGTH = 42;

    String TRACE_PARENT_BINARY_HEADER_NAME = "elasticapmtraceparent";
    int BINARY_FORMAT_EXPECTED_LENGTH = 29;

    Id getTraceId();

    Id getId();

    Id getParentId();

    Id getTransactionId();

    void setServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion);

    boolean asChildOf(byte[] parent);

    boolean asChildOf(String parent);

    void asChildOf(TraceContext parent);

    void addTraceState(String header);
}
