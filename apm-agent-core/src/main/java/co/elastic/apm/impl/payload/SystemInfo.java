package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private final String architecture;
    /**
     * Hostname of the system the agent is running on.
     */
    @JsonProperty("hostname")
    private final String hostname;
    /**
     * Name of the system platform the agent is running on.
     */
    @JsonProperty("platform")
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

}
