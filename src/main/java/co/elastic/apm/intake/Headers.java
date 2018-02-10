
package co.elastic.apm.intake;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * A mapping of HTTP headers of the response object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "content-type"
})
public class Headers {

    @JsonProperty("content-type")
    private String contentType;

    @JsonProperty("content-type")
    public String getContentType() {
        return contentType;
    }

    @JsonProperty("content-type")
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Headers withContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("contentType", contentType).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentType).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Headers) == false) {
            return false;
        }
        Headers rhs = ((Headers) other);
        return new EqualsBuilder().append(contentType, rhs.contentType).isEquals();
    }

}
