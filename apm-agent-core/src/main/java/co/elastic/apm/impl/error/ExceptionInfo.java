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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information about the originally thrown error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionInfo implements Recyclable {

    @JsonProperty("attributes")
    private final Map<String, Object> attributes = new HashMap<>();
    @JsonProperty("stacktrace")
    private final List<Stacktrace> stacktrace = new ArrayList<>();
    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @JsonProperty("code")
    @JsonPropertyDescription("The error code set when the error happened, e.g. database error code.")
    private String code;
    /**
     * The original error message.
     * (Required)
     */
    @JsonProperty("message")
    @JsonPropertyDescription("The original error message.")
    private String message;
    /**
     * Describes the exception type's module namespace.
     */
    @JsonProperty("module")
    @JsonPropertyDescription("Describes the exception type's module namespace.")
    private String module;
    @JsonProperty("type")
    private String type;
    /**
     * Indicator whether the error was caught somewhere in the code or not.
     */
    @JsonProperty("handled")
    @JsonPropertyDescription("Indicator whether the error was caught somewhere in the code or not.")
    private boolean handled;

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @JsonProperty("code")
    public String getCode() {
        return code;
    }

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @JsonProperty("code")
    public void setCode(String code) {
        this.code = code;
    }

    public ExceptionInfo withCode(String code) {
        this.code = code;
        return this;
    }

    /**
     * The original error message.
     * (Required)
     */
    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    /**
     * The original error message.
     * (Required)
     */
    @JsonProperty("message")
    public void setMessage(String message) {
        this.message = message;
    }

    public ExceptionInfo withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Describes the exception type's module namespace.
     */
    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    /**
     * Describes the exception type's module namespace.
     */
    @JsonProperty("module")
    public void setModule(String module) {
        this.module = module;
    }

    public ExceptionInfo withModule(String module) {
        this.module = module;
        return this;
    }

    @JsonProperty("attributes")
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public ExceptionInfo addAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public ExceptionInfo withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Indicator whether the error was caught somewhere in the code or not.
     */
    @JsonProperty("handled")
    public boolean isHandled() {
        return handled;
    }

    /**
     * Indicator whether the error was caught somewhere in the code or not.
     */
    @JsonProperty("handled")
    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public ExceptionInfo withHandled(boolean handled) {
        this.handled = handled;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("code", code)
            .append("message", message)
            .append("module", module)
            .append("attributes", attributes)
            .append("stacktrace", stacktrace)
            .append("type", type)
            .append("handled", handled).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(code)
            .append(stacktrace)
            .append(module)
            .append(handled)
            .append(attributes)
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
            .append(module, rhs.module)
            .append(handled, rhs.handled)
            .append(attributes, rhs.attributes)
            .append(message, rhs.message)
            .append(type, rhs.type).isEquals();
    }

    @Override
    public void resetState() {
        code = null;
        stacktrace.clear();
        module = null;
        handled = false;
        attributes.clear();
        message = null;
        type = null;
    }
}
