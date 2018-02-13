
package co.elastic.apm.intake.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Name and version of the language runtime running this service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "version"
})
public class Runtime {

    /**
     * (Required)
     */
    @JsonProperty("name")
    private String name;
    /**
     * (Required)
     */
    @JsonProperty("version")
    private String version;

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
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Runtime withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * (Required)
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    public Runtime withVersion(String version) {
        this.version = version;
        return this;
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
