
package co.elastic.apm.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({
    "service",
    "process",
    "errors",
    "system"
})
public class Payload {

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    private Service service;
    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    private Process process;
    /**
     * (Required)
     */
    @JsonProperty("errors")
    private List<Error> errors = new ArrayList<Error>();
    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    private SystemInfo system;

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    public Service getService() {
        return service;
    }

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    public void setService(Service service) {
        this.service = service;
    }

    public Payload withService(Service service) {
        this.service = service;
        return this;
    }

    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    public Process getProcess() {
        return process;
    }

    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    public void setProcess(Process process) {
        this.process = process;
    }

    public Payload withProcess(Process process) {
        this.process = process;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("errors")
    public List<Error> getErrors() {
        return errors;
    }

    /**
     * (Required)
     */
    @JsonProperty("errors")
    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public Payload withErrors(List<Error> errors) {
        this.errors = errors;
        return this;
    }

    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    public SystemInfo getSystem() {
        return system;
    }

    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    public void setSystem(SystemInfo system) {
        this.system = system;
    }

    public Payload withSystem(SystemInfo system) {
        this.system = system;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("service", service).append("process", process).append("errors", errors).append("system", system).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(process).append(system).append(service).append(errors).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Payload) == false) {
            return false;
        }
        Payload rhs = ((Payload) other);
        return new EqualsBuilder().append(process, rhs.process).append(system, rhs.system).append(service, rhs.service).append(errors, rhs.errors).isEquals();
    }

}
