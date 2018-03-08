package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static co.elastic.apm.impl.ElasticApmTracer.MS_IN_NANOS;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "context",
    "duration",
    "name",
    "parent",
    "stacktrace",
    "start",
    "type"
})
public class Span implements Recyclable, co.elastic.apm.api.Span {

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    @JsonPropertyDescription("Any other arbitrary data captured by the agent, optionally provided by the user")
    private final SpanContext context = new SpanContext();
    /**
     * List of stack frames with variable attributes (eg: lineno, filename, etc)
     */
    @JsonProperty("stacktrace")
    @JsonPropertyDescription("List of stack frames with variable attributes (eg: lineno, filename, etc)")
    private final List<Stacktrace> stacktrace = new ArrayList<Stacktrace>();

    private transient ElasticApmTracer tracer;
    /**
     * The locally unique ID of the span.
     */
    @JsonProperty("id")
    @JsonPropertyDescription("The locally unique ID of the span.")
    private long id;
    /**
     * Duration of the span in milliseconds
     * (Required)
     */
    @JsonProperty("duration")
    @JsonPropertyDescription("Duration of the span in milliseconds")
    private double duration;
    /**
     * Generic designation of a span in the scope of a transaction
     * (Required)
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Generic designation of a span in the scope of a transaction")
    private String name;
    /**
     * The locally unique ID of the parent of the span.
     */
    @JsonProperty("parent")
    @JsonPropertyDescription("The locally unique ID of the parent of the span.")
    private long parent;
    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    @JsonProperty("start")
    @JsonPropertyDescription("Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds")
    private double start;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)")
    private String type;

    public Span start(ElasticApmTracer tracer, Transaction transaction, Span span, long nanoTime) {
        this.tracer = tracer;
        this.id = ThreadLocalRandom.current().nextLong();
        this.parent = span != null ? span.getId() : 0;
        start = (nanoTime - transaction.getDuration()) / MS_IN_NANOS;
        duration = nanoTime;
        return this;
    }

    /**
     * The locally unique ID of the span.
     */
    @JsonProperty("id")
    public long getId() {
        return id;
    }

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    public SpanContext getContext() {
        return context;
    }

    /**
     * Duration of the span in milliseconds
     * (Required)
     */
    @JsonProperty("duration")
    public double getDuration() {
        return duration;
    }

    /**
     * Duration of the span in milliseconds
     * (Required)
     */
    @JsonProperty("duration")
    public void setDuration(double duration) {
        this.duration = duration;
    }

    public Span withDuration(double duration) {
        this.duration = duration;
        return this;
    }

    /**
     * Generic designation of a span in the scope of a transaction
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Generic designation of a span in the scope of a transaction
     * (Required)
     */
    @Override
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Span withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * The locally unique ID of the parent of the span.
     */
    @JsonProperty("parent")
    public long getParent() {
        return parent;
    }

    /**
     * The locally unique ID of the parent of the span.
     */
    @JsonProperty("parent")
    public void setParent(long parent) {
        this.parent = parent;
    }

    public Span withParent(long parent) {
        this.parent = parent;
        return this;
    }

    /**
     * List of stack frames with variable attributes (eg: lineno, filename, etc)
     */
    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    @JsonProperty("start")
    public double getStart() {
        return start;
    }

    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    @JsonProperty("start")
    public void setStart(double start) {
        this.start = start;
    }

    public Span withStart(double start) {
        this.start = start;
        return this;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @Override
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public void end() {
        end(System.nanoTime());
    }

    public void end(long nanoTime) {
        this.duration = (nanoTime - duration) / MS_IN_NANOS;
        this.tracer.endSpan(this);
    }

    @Override
    public void close() {
        end();
    }

    public Span withType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("id", id).append("context", context).append("duration", duration).append("name", name).append("parent", parent).append("stacktrace", stacktrace).append("start", start).append("type", type).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(duration).append(parent).append(stacktrace).append(context).append(name).append(start).append(id).append(type).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Span) == false) {
            return false;
        }
        Span rhs = ((Span) other);
        return new EqualsBuilder().append(duration, rhs.duration).append(parent, rhs.parent).append(stacktrace, rhs.stacktrace).append(context, rhs.context).append(name, rhs.name).append(start, rhs.start).append(id, rhs.id).append(type, rhs.type).isEquals();
    }

    @Override
    public void resetState() {
        id = 0;
        context.resetState();
        duration = 0;
        name = null;
        parent = 0;
        stacktrace.clear();
        start = 0;
        type = null;
        tracer = null;
    }

}
