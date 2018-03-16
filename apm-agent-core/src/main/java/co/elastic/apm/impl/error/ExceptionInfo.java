package co.elastic.apm.impl.error;

import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Information about the originally thrown error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionInfo implements Recyclable {

    @JsonProperty("stacktrace")
    private final List<Stacktrace> stacktrace = new ArrayList<>();
    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @Nullable
    @JsonProperty("code")
    private String code;
    /**
     * The original error message.
     * (Required)
     */
    @Nullable
    @JsonProperty("message")
    private String message;
    /**
     * Describes the exception type's module namespace.
     */
    @Nullable
    @JsonProperty("type")
    private String type;

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @Nullable
    @JsonProperty("code")
    public String getCode() {
        return code;
    }

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    public ExceptionInfo withCode(String code) {
        this.code = code;
        return this;
    }

    /**
     * The original error message.
     * (Required)
     */
    @Nullable
    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    /**
     * The original error message.
     * (Required)
     */
    public ExceptionInfo withMessage(@Nullable String message) {
        this.message = message;
        return this;
    }

    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @Nullable
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public ExceptionInfo withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("code", code)
            .append("message", message)
            .append("stacktrace", stacktrace)
            .append("type", type)
            .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(code)
            .append(stacktrace)
            .append(message)
            .append(type).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ExceptionInfo) == false) {
            return false;
        }
        ExceptionInfo rhs = ((ExceptionInfo) other);
        return new EqualsBuilder()
            .append(code, rhs.code)
            .append(stacktrace, rhs.stacktrace)
            .append(message, rhs.message)
            .append(type, rhs.type).isEquals();
    }

    @Override
    public void resetState() {
        code = null;
        stacktrace.clear();
        message = null;
        type = null;
    }
}
