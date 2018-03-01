package co.elastic.apm.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.net.InetAddress;

/**
 * Information about the system the agent is running on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemInfo {

    /**
     * Architecture of the system the agent is running on.
     */
    @JsonProperty("architecture")
    @JsonPropertyDescription("Architecture of the system the agent is running on.")
    private final String architecture;
    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    @JsonPropertyDescription("Hostname of the system the agent is running on.")
    private final String hostname;
    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
    @JsonPropertyDescription("Name of the system platform the agent is running on.")
    private final String platform;

    public SystemInfo(String architecture, String hostname, String platform) {
        this.architecture = architecture;
        this.hostname = hostname;
        this.platform = platform;
    }

    public static SystemInfo create() {
        return new SystemInfo(System.getProperty("os.arch"), System.getProperty("os.name"), getNameOfLocalHost());
    }

    private static String getNameOfLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return getHostNameFromEnv();
        }
    }

    private static String getHostNameFromEnv() {
        // try environment properties.
        String host = System.getenv("COMPUTERNAME");
        if (host == null) {
            host = System.getenv("HOSTNAME");
        }
        if (host == null) {
            host = System.getenv("HOST");
        }
        return host;
    }

    /**
     * Architecture of the system the agent is running on.
     */
    @JsonProperty("architecture")
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
    public String getPlatform() {
        return platform;
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
        if ((other instanceof SystemInfo) == false) {
            return false;
        }
        SystemInfo rhs = ((SystemInfo) other);
        return new EqualsBuilder().append(hostname, rhs.hostname).append(platform, rhs.platform).append(architecture, rhs.architecture).isEquals();
    }

}
