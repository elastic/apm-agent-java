
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.ErrorCapture;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.Process;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.List;


/**
 * Errors payload
 * <p>
 * List of errors wrapped in an object containing some other attributes normalized away from the errors themselves
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorPayload extends Payload {

    /**
     * (Required)
     */
    @JsonProperty("errors")
    private final List<ErrorCapture> errors = new ArrayList<ErrorCapture>();

    public ErrorPayload(Process process, Service service, SystemInfo system) {
        super(process, service, system);
    }

    /**
     * (Required)
     */
    @JsonProperty("errors")
    public List<ErrorCapture> getErrors() {
        return errors;
    }

    @Override
    public List<? extends Recyclable> getPayloadObjects() {
        return errors;
    }

    @Override
    public void recycle() {
        for (ErrorCapture error : errors) {
            error.recycle();
        }
    }

    @Override
    public void resetState() {
        errors.clear();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("service", service)
            .append("process", process)
            .append("errors", errors)
            .append("system", system).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(process)
            .append(system)
            .append(service)
            .append(errors).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ErrorPayload) == false) {
            return false;
        }
        ErrorPayload rhs = ((ErrorPayload) other);
        return new EqualsBuilder()
            .append(process, rhs.process)
            .append(system, rhs.system)
            .append(service, rhs.service)
            .append(errors, rhs.errors).isEquals();
    }
}
