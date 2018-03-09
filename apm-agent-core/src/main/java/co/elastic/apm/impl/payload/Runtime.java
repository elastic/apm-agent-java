
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Name and version of the language runtime running this service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Runtime {

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

    public Runtime(String name, String version) {
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

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("name", name).append("version", version).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(name).append(version).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Runtime) == false) {
            return false;
        }
        Runtime rhs = ((Runtime) other);
        return new EqualsBuilder().append(name, rhs.name).append(version, rhs.version).isEquals();
    }

}
