
package co.elastic.apm.intake.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Name and version of the Elastic APM agent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "version"
})
public class Agent {

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the Elastic APM agent, e.g. \"Python\"")
    private String name;
    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the Elastic APM agent, e.g.\"1.0.0\"")
    private String version;

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Agent withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    public Agent withVersion(String version) {
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
        if ((other instanceof Agent) == false) {
            return false;
        }
        Agent rhs = ((Agent) other);
        return new EqualsBuilder().append(name, rhs.name).append(version, rhs.version).isEquals();
    }

}
