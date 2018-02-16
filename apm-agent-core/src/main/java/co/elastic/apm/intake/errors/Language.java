
package co.elastic.apm.intake.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Name and version of the programming language used
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "version"
})
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
        if ((other instanceof Language) == false) {
            return false;
        }
        Language rhs = ((Language) other);
        return new EqualsBuilder().append(name, rhs.name).append(version, rhs.version).isEquals();
    }

}
