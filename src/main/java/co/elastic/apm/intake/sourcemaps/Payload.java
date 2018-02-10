
package co.elastic.apm.intake.sourcemaps;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Sourcemap Metadata
 * <p>
 * Sourcemap Metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "bundle_filepath",
    "service_version",
    "service_name"
})
public class Payload {

    /**
     * relative path of the minified bundle file
     * (Required)
     */
    @JsonProperty("bundle_filepath")
    @JsonPropertyDescription("relative path of the minified bundle file")
    private String bundleFilepath;
    /**
     * Version of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_version")
    @JsonPropertyDescription("Version of the service emitting this event")
    private String serviceVersion;
    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_name")
    @JsonPropertyDescription("Immutable name of the service emitting this event")
    private String serviceName;

    /**
     * relative path of the minified bundle file
     * (Required)
     */
    @JsonProperty("bundle_filepath")
    public String getBundleFilepath() {
        return bundleFilepath;
    }

    /**
     * relative path of the minified bundle file
     * (Required)
     */
    @JsonProperty("bundle_filepath")
    public void setBundleFilepath(String bundleFilepath) {
        this.bundleFilepath = bundleFilepath;
    }

    public Payload withBundleFilepath(String bundleFilepath) {
        this.bundleFilepath = bundleFilepath;
        return this;
    }

    /**
     * Version of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_version")
    public String getServiceVersion() {
        return serviceVersion;
    }

    /**
     * Version of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_version")
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public Payload withServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
        return this;
    }

    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_name")
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("service_name")
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Payload withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("bundleFilepath", bundleFilepath).append("serviceVersion", serviceVersion).append("serviceName", serviceName).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(serviceVersion).append(bundleFilepath).append(serviceName).toHashCode();
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
        return new EqualsBuilder().append(serviceVersion, rhs.serviceVersion).append(bundleFilepath, rhs.bundleFilepath).append(serviceName, rhs.serviceName).isEquals();
    }

}
