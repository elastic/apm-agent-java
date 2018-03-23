
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Name and version of the programming language used
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Language {

    /**
     * (Required)
     */
    @JsonProperty("name")
    private final String name;
    @JsonProperty("version")
    private final String version;

    public Language(String name, String version) {
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

    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

}
