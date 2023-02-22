package co.elastic.apm.plugin.spi;

public interface SpanContext extends AbstractContext {

    ServiceTarget getServiceTarget();

    Destination getDestination();

    Db getDb();

    Http getHttp();
}
