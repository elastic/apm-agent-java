package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static co.elastic.apm.impl.ElasticApmTracer.MS_IN_NANOS;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Span implements Recyclable, co.elastic.apm.api.Span {

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    private final SpanContext context = new SpanContext();
    /**
     * List of stack frames with variable attributes (eg: lineno, filename, etc)
     */
    @JsonProperty("stacktrace")
    private final List<Stacktrace> stacktrace = new ArrayList<Stacktrace>();

    private transient ElasticApmTracer tracer;
    private transient boolean sampled;

    /**
     * The locally unique ID of the span.
     */
    @JsonProperty("id")
    private final SpanId id = new SpanId();
    /**
     * Duration of the span in milliseconds
     * (Required)
     */
    @JsonProperty("duration")
    private double duration;
    /**
     * Generic designation of a span in the scope of a transaction
     * (Required)
     */
    @JsonProperty("name")
    private String name;
    /**
     * The locally unique ID of the parent of the span.
     */
    @JsonProperty("parent")
    @Nonnull
    private SpanId parent;
    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    @JsonProperty("start")
    private double start;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @JsonProperty("type")
    private String type;

    public Span start(ElasticApmTracer tracer, Transaction transaction, @Nullable Span span, long nanoTime, boolean dropped) {
        this.tracer = tracer;
        this.id.setToRandomValue();
        if (span != null) {
            this.parent.copyFrom(span.getId());
        }
        this.sampled = transaction.isSampled() && !dropped;
        start = (nanoTime - transaction.getDuration()) / MS_IN_NANOS;
        duration = nanoTime;
        return this;
    }

    /**
     * The locally unique ID of the span.
     */
    @JsonProperty("id")
    public SpanId getId() {
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
    public void setName(String name) {
        withName(name);
    }

    public Span withName(String name) {
        if (!sampled) {
            return this;
        }
        this.name = name;
        return this;
    }

    /**
     * The locally unique ID of the parent of the span.
     */
    @JsonProperty("parent")
    public SpanId getParent() {
        return parent;
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
    public void setType(String type) {
        withType(type);
    }

    @Override
    public void end() {
        end(System.nanoTime());
    }

    public void end(long nanoTime) {
        if (isSampled()) {
            this.duration = (nanoTime - duration) / MS_IN_NANOS;
        }
        this.tracer.endSpan(this);
    }

    @Override
    public void close() {
        end();
    }

    public Span withType(String type) {
        if (!sampled) {
            return this;
        }
        this.type = type;
        return this;
    }

    @JsonIgnore
    public boolean isSampled() {
        return sampled;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("id", id)
            .append("context", context)
            .append("duration", duration)
            .append("name", name)
            .append("parent", parent)
            .append("stacktrace", stacktrace)
            .append("start", start)
            .append("type", type).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(duration)
            .append(parent)
            .append(stacktrace)
            .append(context)
            .append(name)
            .append(start)
            .append(id)
            .append(type).toHashCode();
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
        return new EqualsBuilder()
            .append(duration, rhs.duration)
            .append(parent, rhs.parent)
            .append(stacktrace, rhs.stacktrace)
            .append(context, rhs.context)
            .append(name, rhs.name)
            .append(start, rhs.start)
            .append(id, rhs.id)
            .append(type, rhs.type).isEquals();
    }

    @Override
    public void resetState() {
        id.resetState();
        context.resetState();
        duration = 0;
        name = null;
        parent.resetState();
        stacktrace.clear();
        start = 0;
        type = null;
        tracer = null;
        sampled = false;
    }

}
