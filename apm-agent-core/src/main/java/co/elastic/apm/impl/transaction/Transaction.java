package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Transaction implements Recyclable, co.elastic.apm.api.Transaction {

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    private final Context context = new Context();
    /**
     * Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @JsonProperty("timestamp")
    private final Date timestamp = new Date(0);
    @JsonProperty("spans")
    private final List<Span> spans = new ArrayList<Span>();
    /**
     * A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.
     */
    @JsonProperty("marks")
    private final Map<String, Object> marks = new HashMap<>();
    @JsonProperty("span_count")
    private final SpanCount spanCount = new SpanCount();
    private transient ElasticApmTracer tracer;
    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    @JsonProperty("duration")
    private double duration;
    /**
     * UUID for the transaction, referred by its spans
     * (Required)
     */
    @JsonProperty("id")
    // TODO create value type for id
    private UUID id;
    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    @JsonProperty("name")
    private String name;
    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @JsonProperty("result")
    private String result;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @JsonProperty("type")
    private String type;
    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    @JsonProperty("sampled")
    private boolean sampled;

    public Transaction start(ElasticApmTracer tracer, long startTimestampNanos, boolean sampled) {
        this.tracer = tracer;
        this.duration = startTimestampNanos;
        this.sampled = sampled;
        this.timestamp.setTime(System.currentTimeMillis());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        id = new UUID(random.nextLong(), random.nextLong());
        return this;
    }

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    public Context getContext() {
        return context;
    }

    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    @JsonProperty("duration")
    public double getDuration() {
        return duration;
    }

    /**
     * UUID for the transaction, referred by its spans
     * (Required)
     */
    @JsonProperty("id")
    public UUID getId() {
        return id;
    }


    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    public Transaction withName(String name) {
        if (!sampled) {
            return this;
        }
        this.name = name;
        return this;
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @JsonProperty("result")
    public String getResult() {
        return result;
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    public Transaction withResult(String result) {
        if (!sampled) {
            return this;
        }
        this.result = result;
        return this;
    }

    /**
     * Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonProperty("timestamp")
    public Date getTimestamp() {
        return timestamp;
    }

    public Transaction withTimestamp(long timestampEpoch) {
        if (!sampled) {
            return this;
        }
        this.timestamp.setTime(timestampEpoch);
        return this;
    }

    @JsonProperty("spans")
    public List<Span> getSpans() {
        return spans;
    }

    public Transaction addSpan(Span span) {
        if (!sampled) {
            spanCount.getDropped().increment();
        } else {
            spans.add(span);
        }
        return this;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @JsonProperty("type")
    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public void addTag(String key, String value) {
        if (!sampled) {
            return;
        }
        getContext().getTags().put(key, value);
    }

    @Override
    public void setUser(String id, String email, String username) {
        if (!sampled) {
            return;
        }
        getContext().getUser().withId(id).withEmail(email).withUsername(username);
    }

    @Override
    public void end() {
        this.duration = (System.nanoTime() - duration) / ElasticApmTracer.MS_IN_NANOS;
        if (!sampled) {
            context.resetState();
        }
        this.tracer.endTransaction(this);
    }

    @Override
    public void close() {
        end();
    }

    public Transaction withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.
     */
    @JsonProperty("marks")
    public Map<String, Object> getMarks() {
        return marks;
    }

    /**
     * Transactions that are 'sampled' will include all available information.
     * Transactions that are not sampled will not have 'spans' or 'context'.
     * Defaults to true.
     */
    @JsonProperty("sampled")
    public boolean isSampled() {
        return sampled;
    }

    @JsonProperty("span_count")
    public SpanCount getSpanCount() {
        return spanCount;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("context", context)
            .append("duration", duration)
            .append("id", id)
            .append("name", name)
            .append("result", result)
            .append("timestamp", timestamp)
            .append("spans", spans)
            .append("type", type)
            .append("marks", marks)
            .append("sampled", sampled)
            .append("spanCount", spanCount).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(duration)
            .append(result)
            .append(spans)
            .append(spanCount)
            .append(context)
            .append(name)
            .append(id)
            .append(marks)
            .append(type)
            .append(sampled)
            .append(timestamp).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Transaction) == false) {
            return false;
        }
        Transaction rhs = ((Transaction) other);
        return new EqualsBuilder()
            .append(duration, rhs.duration)
            .append(result, rhs.result)
            .append(spans, rhs.spans)
            .append(spanCount, rhs.spanCount)
            .append(context, rhs.context)
            .append(name, rhs.name)
            .append(id, rhs.id)
            .append(marks, rhs.marks)
            .append(type, rhs.type)
            .append(sampled, rhs.sampled)
            .append(timestamp, rhs.timestamp).isEquals();
    }

    @Override
    public void resetState() {
        context.resetState();
        duration = 0;
        id = null;
        name = null;
        result = null;
        timestamp.setTime(0);
        spans.clear();
        type = null;
        marks.clear();
        sampled = true;
        spanCount.resetState();
        tracer = null;
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }

}
