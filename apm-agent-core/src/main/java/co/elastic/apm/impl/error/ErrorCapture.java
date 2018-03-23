package co.elastic.apm.impl.error;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;
import java.util.Date;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorCapture implements Recyclable {

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    private final Context context = new Context();
    /**
     * Information about the originally thrown error.
     */
    @JsonProperty("exception")
    private final ExceptionInfo exception = new ExceptionInfo();
    /**
     * Additional information added when logging the error.
     */
    @JsonProperty("log")
    private final Log log = new Log();
    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @JsonProperty("timestamp")
    private final Date timestamp = new Date();
    /**
     * Data for correlating errors with transactions
     */
    @JsonProperty("transaction")
    private final TransactionReference transaction = new TransactionReference();
    @Nullable
    private transient ElasticApmTracer tracer;
    /**
     * ID for the error
     */
    @JsonProperty("id")
    private final TransactionId id = new TransactionId();

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
     * Information about the originally thrown error.
     */
    @JsonProperty("exception")
    public ExceptionInfo getException() {
        return exception;
    }


    /**
     * UUID for the error
     */
    @JsonProperty("id")
    public TransactionId getId() {
        return id;
    }


    /**
     * Additional information added when logging the error.
     */
    @JsonProperty("log")
    public Log getLog() {
        return log;
    }


    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonProperty("timestamp")
    public Date getTimestamp() {
        return timestamp;
    }

    public ErrorCapture withTimestamp(long epochMs) {
        this.timestamp.setTime(epochMs);
        return this;
    }

    /**
     * Data for correlating errors with transactions
     */
    @JsonProperty("transaction")
    public TransactionReference getTransaction() {
        return transaction;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("context", context)
            .append("exception", exception)
            .append("id", id)
            .append("log", log)
            .append("timestamp", timestamp)
            .append("transaction", transaction).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(exception)
            .append(log)
            .append(context)
            .append(id)
            .append(transaction)
            .append(timestamp).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ErrorCapture) == false) {
            return false;
        }
        ErrorCapture rhs = ((ErrorCapture) other);
        return new EqualsBuilder()
            .append(exception, rhs.exception)
            .append(log, rhs.log)
            .append(context, rhs.context)
            .append(id, rhs.id)
            .append(transaction, rhs.transaction)
            .append(timestamp, rhs.timestamp).isEquals();
    }

    @Override
    public void resetState() {
        exception.resetState();
        log.resetState();
        context.resetState();
        id.resetState();
        transaction.resetState();
        timestamp.setTime(0);
        tracer = null;
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }
}
