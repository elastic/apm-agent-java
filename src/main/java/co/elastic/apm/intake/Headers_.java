
package co.elastic.apm.intake;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Should include any headers sent by the requester. Cookies will be taken by headers if supplied.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "content-type",
    "cookie",
    "user-agent"
})
public class Headers_ {

    @JsonProperty("content-type")
    private String contentType;
    /**
     * Cookies sent with the request. It is expected to have values delimited by semicolons.
     */
    @JsonProperty("cookie")
    @JsonPropertyDescription("Cookies sent with the request. It is expected to have values delimited by semicolons.")
    private String cookie;
    @JsonProperty("user-agent")
    private String userAgent;

    @JsonProperty("content-type")
    public String getContentType() {
        return contentType;
    }

    @JsonProperty("content-type")
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Headers_ withContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Cookies sent with the request. It is expected to have values delimited by semicolons.
     */
    @JsonProperty("cookie")
    public String getCookie() {
        return cookie;
    }

    /**
     * Cookies sent with the request. It is expected to have values delimited by semicolons.
     */
    @JsonProperty("cookie")
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public Headers_ withCookie(String cookie) {
        this.cookie = cookie;
        return this;
    }

    @JsonProperty("user-agent")
    public String getUserAgent() {
        return userAgent;
    }

    @JsonProperty("user-agent")
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Headers_ withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("contentType", contentType).append("cookie", cookie).append("userAgent", userAgent).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(userAgent).append(cookie).append(contentType).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Headers_) == false) {
            return false;
        }
        Headers_ rhs = ((Headers_) other);
        return new EqualsBuilder().append(userAgent, rhs.userAgent).append(cookie, rhs.cookie).append(contentType, rhs.contentType).isEquals();
    }

}
