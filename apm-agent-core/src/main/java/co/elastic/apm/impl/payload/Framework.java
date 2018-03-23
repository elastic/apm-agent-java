
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Name and version of the web framework used
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Framework {

    /**
     * (Required)
     */
    @JsonProperty("name")
    private final String name;
    /**
     * (Required)
     */
    @JsonProperty("version")
    private final String version;

    public Framework(String name, String version) {
        this.name = name;
        this.version = version;
    }

    /**
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * (Required)
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

}
