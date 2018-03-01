package co.elastic.apm.impl;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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

    private transient ElasticApmTracer tracer;
    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    @JsonPropertyDescription("Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user")
    private final Context context = new Context();
    /**
     * Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ")
    private final Date timestamp = new Date(0);
    @JsonProperty("spans")
    private final List<Span> spans = new ArrayList<Span>();
    /**
     * A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.
     */
    @JsonProperty("marks")
    @JsonPropertyDescription("A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.")
    private final Map<String, Object> marks = new HashMap<>();
    @JsonProperty("span_count")
    private final SpanCount spanCount = new SpanCount();
    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    @JsonProperty("duration")
    @JsonPropertyDescription("How long the transaction took to complete, in ms with 3 decimal points")
    private double duration;
    /**
     * UUID for the transaction, referred by its spans
     * (Required)
     */
    @JsonProperty("id")
    @JsonPropertyDescription("UUID for the transaction, referred by its spans")
    // TODO create value type for id
    private UUID id;
    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')")
    private String name;
    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @JsonProperty("result")
    @JsonPropertyDescription("The result of the transaction. HTTP status code for HTTP-related transactions.")
    private String result;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)")
    private String type;
    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    @JsonProperty("sampled")
    @JsonPropertyDescription("Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.")
    private boolean sampled;

    Transaction() {
    }

    public Transaction start(ElasticApmTracer tracer, long startTimestampNanos) {
        this.tracer = tracer;
        this.duration = startTimestampNanos;
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
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    @JsonProperty("duration")
    public void setDuration(double duration) {
        this.duration = duration;
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
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Transaction withName(String name) {
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
    @JsonProperty("result")
    public void setResult(String result) {
        this.result = result;
    }

    public Transaction withResult(String result) {
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
        this.timestamp.setTime(timestampEpoch);
        return this;
    }

    @JsonProperty("spans")
    public List<Span> getSpans() {
        return spans;
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
        getContext().getTags().put(key, value);
    }

    @Override
    public void setUser(String id, String email, String username) {
        getContext().getUser().withId(id).withEmail(email).withUsername(username);
    }

    @Override
    public void end() {
        this.duration = (System.nanoTime() - duration) / ElasticApmTracer.MS_IN_NANOS;
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
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    @JsonProperty("sampled")
    public boolean isSampled() {
        return sampled;
    }

    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    @JsonProperty("sampled")
    public void setSampled(boolean sampled) {
        this.sampled = sampled;
    }

    public Transaction withSampled(boolean sampled) {
        this.sampled = sampled;
        return this;
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
    }

    public void recycle() {
        tracer.recycle(this);
    }

}
