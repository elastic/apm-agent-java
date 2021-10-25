package co.elastic.apm.agent.impl.transaction;

/**
 * Mirrors OpenTelemetry span kind
 */
public enum OTelSpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER
}
