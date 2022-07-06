package co.elastic.apm.agent.mongodb;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import com.mongodb.ServerAddress;

import javax.annotation.Nullable;

public class MongoHelper {

    private final Tracer tracer;

    public MongoHelper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Span startSpan(@Nullable String database, @Nullable  String collection, @Nullable String command, ServerAddress serverAddress) {
        Span span = null;
        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan != null) {
            span = activeSpan.createExitSpan();
        }

        if (span == null) {
            return null;
        }

        span.withType("db")
            .withSubtype("mongodb")
            .withAction(command)
            .getContext().getDb().withType("mongodb");

        span.getContext().getServiceTarget()
            .withType("mongodb")
            .withName(database);

        span.getContext().getDb()
            .withInstance(database);

        span.getContext().getDestination()
            .withAddress(serverAddress.getHost())
            .withPort(serverAddress.getPort());

        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (name != null) {
            appendToName(name, database);
            appendToName(name, collection);
            appendToName(name, command);
        }

        return span.activate();
    }

    private static void appendToName(StringBuilder name, @Nullable String value) {
        if (value == null) {
            return;
        }
        if (name.length() > 0) {
            name.append('.');
        }
        name.append(value);
    }
}
