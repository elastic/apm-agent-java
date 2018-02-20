
package co.elastic.apm.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Additional information added when logging the error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "level",
    "logger_name",
    "message",
    "param_message",
    "stacktrace"
})
public class Log {

    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    @JsonPropertyDescription("The severity of the record.")
    private Level level = Level.fromValue("error");
    /**
     * The name of the logger instance used.
     */
    @JsonProperty("logger_name")
    @JsonPropertyDescription("The name of the logger instance used.")
    private String loggerName = "default";
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
    @JsonProperty("stacktrace")
    private List<Stacktrace> stacktrace = new ArrayList<Stacktrace>();

    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    public Level getLevel() {
        return level;
    }

    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    public void setLevel(Level level) {
        this.level = level;
    }

    public Log withLevel(Level level) {
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
    @JsonProperty("logger_name")
    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

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
    @JsonProperty("message")
    public void setMessage(String message) {
        this.message = message;
    }

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
    @JsonProperty("param_message")
    public void setParamMessage(String paramMessage) {
        this.paramMessage = paramMessage;
    }

    public Log withParamMessage(String paramMessage) {
        this.paramMessage = paramMessage;
        return this;
    }

    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @JsonProperty("stacktrace")
    public void setStacktrace(List<Stacktrace> stacktrace) {
        this.stacktrace = stacktrace;
    }

    public Log withStacktrace(List<Stacktrace> stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("level", level).append("loggerName", loggerName).append("message", message).append("paramMessage", paramMessage).append("stacktrace", stacktrace).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(loggerName).append(message).append(paramMessage).append(stacktrace).append(level).toHashCode();
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
        return new EqualsBuilder().append(loggerName, rhs.loggerName).append(message, rhs.message).append(paramMessage, rhs.paramMessage).append(stacktrace, rhs.stacktrace).append(level, rhs.level).isEquals();
    }

    public enum Level {

        DEBUG("debug"),
        INFO("info"),
        WARNING("warning"),
        ERROR("error"),
        FATAL("fatal");
        private final String value;
        private final static Map<String, Level> CONSTANTS = new HashMap<String, Level>();

        static {
            for (Level c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Level(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Level fromValue(String value) {
            Level constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
