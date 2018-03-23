
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Name and version of the Elastic APM agent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Agent {

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    @JsonProperty("name")
    private final String name;
    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    @JsonProperty("version")
    private final String version;

    public Agent(String name, String version) {
        this.name = name;
        this.version = version;
    }

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

}
