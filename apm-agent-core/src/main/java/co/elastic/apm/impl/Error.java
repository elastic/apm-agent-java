
package co.elastic.apm.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.Date;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "context",
    "culprit",
    "exception",
    "id",
    "log",
    "timestamp",
    "transaction"
})
public class Error {

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    @JsonPropertyDescription("Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user")
    private Context context;
    /**
     * Function call which was the primary perpetrator of this event.
     */
    @JsonProperty("culprit")
    @JsonPropertyDescription("Function call which was the primary perpetrator of this event.")
    private String culprit;
    /**
     * Information about the originally thrown error.
     */
    @JsonProperty("exception")
    @JsonPropertyDescription("Information about the originally thrown error.")
    private ExceptionInfo exception;
    /**
     * UUID for the error
     */
    @JsonProperty("id")
    @JsonPropertyDescription("UUID for the error")
    private String id;
    /**
     * Additional information added when logging the error.
     */
    @JsonProperty("log")
    @JsonPropertyDescription("Additional information added when logging the error.")
    private Log log;
    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ")
    private Date timestamp;
    /**
     * Data for correlating errors with transactions
     */
    @JsonProperty("transaction")
    @JsonPropertyDescription("Data for correlating errors with transactions")
    private TransactionReference transaction;

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
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @JsonProperty("context")
    public void setContext(Context context) {
        this.context = context;
    }

    public Error withContext(Context context) {
        this.context = context;
        return this;
    }

    /**
     * Function call which was the primary perpetrator of this event.
     */
    @JsonProperty("culprit")
    public String getCulprit() {
        return culprit;
    }

    /**
     * Function call which was the primary perpetrator of this event.
     */
    @JsonProperty("culprit")
    public void setCulprit(String culprit) {
        this.culprit = culprit;
    }

    public Error withCulprit(String culprit) {
        this.culprit = culprit;
        return this;
    }

    /**
     * Information about the originally thrown error.
     */
    @JsonProperty("exception")
    public ExceptionInfo getException() {
        return exception;
    }

    /**
     * Information about the originally thrown error.
     */
    @JsonProperty("exception")
    public void setException(ExceptionInfo exception) {
        this.exception = exception;
    }

    public Error withException(ExceptionInfo exception) {
        this.exception = exception;
        return this;
    }

    /**
     * UUID for the error
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * UUID for the error
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Error withId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Additional information added when logging the error.
     */
    @JsonProperty("log")
    public Log getLog() {
        return log;
    }

    /**
     * Additional information added when logging the error.
     */
    @JsonProperty("log")
    public void setLog(Log log) {
        this.log = log;
    }

    public Error withLog(Log log) {
        this.log = log;
        return this;
    }

    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonProperty("timestamp")
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    @JsonProperty("timestamp")
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Error withTimestamp(Date timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Data for correlating errors with transactions
     */
    @JsonProperty("transaction")
    public TransactionReference getTransaction() {
        return transaction;
    }

    /**
     * Data for correlating errors with transactions
     */
    @JsonProperty("transaction")
    public void setTransaction(TransactionReference transaction) {
        this.transaction = transaction;
    }

    public Error withTransaction(TransactionReference transaction) {
        this.transaction = transaction;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("context", context).append("culprit", culprit).append("exception", exception).append("id", id).append("log", log).append("timestamp", timestamp).append("transaction", transaction).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(exception).append(culprit).append(log).append(context).append(id).append(transaction).append(timestamp).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Error) == false) {
            return false;
        }
        Error rhs = ((Error) other);
        return new EqualsBuilder().append(exception, rhs.exception).append(culprit, rhs.culprit).append(log, rhs.log).append(context, rhs.context).append(id, rhs.id).append(transaction, rhs.transaction).append(timestamp, rhs.timestamp).isEquals();
    }

}
