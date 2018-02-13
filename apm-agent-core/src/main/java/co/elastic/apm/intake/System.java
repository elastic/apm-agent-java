
package co.elastic.apm.intake;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * System
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "architecture",
    "hostname",
    "platform"
})
// TODO: make immutable
public class System {

    /**
     * Architecture of the system the agent is running on.
     */
    @JsonProperty("architecture")
    @JsonPropertyDescription("Architecture of the system the agent is running on.")
    private String architecture;
    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    @JsonPropertyDescription("Hostname of the system the agent is running on.")
    private String hostname;
    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
    @JsonPropertyDescription("Name of the system platform the agent is running on.")
    private String platform;

    /**
     * Architecture of the system the agent is running on.
     */
    @JsonProperty("architecture")
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Architecture of the system the agent is running on.
     */
    @JsonProperty("architecture")
    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public System withArchitecture(String architecture) {
        this.architecture = architecture;
        return this;
    }

    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public System withHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
    public String getPlatform() {
        return platform;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public System withPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("architecture", architecture).append("hostname", hostname).append("platform", platform).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(hostname).append(platform).append(architecture).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof System) == false) {
            return false;
        }
        System rhs = ((System) other);
        return new EqualsBuilder().append(hostname, rhs.hostname).append(platform, rhs.platform).append(architecture, rhs.architecture).isEquals();
    }

}
