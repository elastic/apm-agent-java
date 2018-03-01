package co.elastic.apm.impl.error;

import co.elastic.apm.impl.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Additional information added when logging the error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Log implements Recyclable {

    @JsonProperty("stacktrace")
    private final List<Stacktrace> stacktrace = new ArrayList<>();
    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    @JsonPropertyDescription("The severity of the record.")
    private String level;
    /**
     * The name of the logger instance used.
     */
    @JsonProperty("logger_name")
    @JsonPropertyDescription("The name of the logger instance used.")
    private String loggerName;
    /**
     * The additionally logged error message.
     * (Required)
     */
    @JsonProperty("message")
    @JsonPropertyDescription("The additionally logged error message.")
    private String message;
    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    @JsonProperty("param_message")
    @JsonPropertyDescription("A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.")
    private String paramMessage;

    Log() {
        resetState();
    }

    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    public String getLevel() {
        return level;
    }

    /**
     * The severity of the record.
     */
    public Log withLevel(String level) {
        this.level = level;
        return this;
    }

    /**
     * The name of the logger instance used.
     */
    @JsonProperty("logger_name")
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * The name of the logger instance used.
     */
    public Log withLoggerName(String loggerName) {
        this.loggerName = loggerName;
        return this;
    }

    /**
     * The additionally logged error message.
     * (Required)
     */
    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    /**
     * The additionally logged error message.
     * (Required)
     */
    public Log withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    @JsonProperty("param_message")
    public String getParamMessage() {
        return paramMessage;
    }

    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    public Log withParamMessage(String paramMessage) {
        this.paramMessage = paramMessage;
        return this;
    }

    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("level", level)
            .append("loggerName", loggerName)
            .append("message", message)
            .append("paramMessage", paramMessage)
            .append("stacktrace", stacktrace).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(loggerName)
            .append(message)
            .append(paramMessage)
            .append(stacktrace)
            .append(level)
            .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Log) == false) {
            return false;
        }
        Log rhs = ((Log) other);
        return new EqualsBuilder()
            .append(loggerName, rhs.loggerName)
            .append(message, rhs.message)
            .append(paramMessage, rhs.paramMessage)
            .append(stacktrace, rhs.stacktrace)
            .append(level, rhs.level).isEquals();
    }

    @Override
    public void resetState() {
        loggerName = "default";
        message = null;
        paramMessage = null;
        stacktrace.clear();
        level = "error";
    }
}
