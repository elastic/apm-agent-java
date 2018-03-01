
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;


/**
 * Context
 * <p>
 * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Context implements Recyclable {

    /**
     * An arbitrary mapping of additional metadata to store with the event.
     */
    @JsonProperty("custom")
    @JsonPropertyDescription("An arbitrary mapping of additional metadata to store with the event.")
    private final Map<String, Object> custom = new HashMap<>();
    @JsonProperty("response")
    private final Response response = new Response();
    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    @JsonProperty("request")
    @JsonPropertyDescription("If a log record was generated as a result of a http request, the http interface can be used to collect this information.")
    private final Request request = new Request();
    /**
     * A flat mapping of user-defined tags with string values.
     */
    @JsonProperty("tags")
    @JsonPropertyDescription("A flat mapping of user-defined tags with string values.")
    private final Map<String, String> tags = new HashMap<>();
    /**
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
    @JsonProperty("user")
    @JsonPropertyDescription("Describes the authenticated User for a request.")
    private final User user = new User();

    /**
     * An arbitrary mapping of additional metadata to store with the event.
     */
    @JsonProperty("custom")
    public Map<String, Object> getCustom() {
        return custom;
    }

    @JsonProperty("response")
    public Response getResponse() {
        return response;
    }

    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    @JsonProperty("request")
    public Request getRequest() {
        return request;
    }

    /**
     * A flat mapping of user-defined tags with string values.
     */
    @JsonProperty("tags")
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
    @JsonProperty("user")
    public User getUser() {
        return user;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this).append("custom", custom).append("response", response).append("request", request).append("tags", tags).append("user", user).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(request).append(user).append(response).append(custom).append(tags).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Context) == false) {
            return false;
        }
        Context rhs = ((Context) other);
        return new EqualsBuilder().append(request, rhs.request).append(user, rhs.user).append(response, rhs.response).append(custom, rhs.custom).append(tags, rhs.tags).isEquals();
    }

    @Override
    public void resetState() {
        custom.clear();
        response.resetState();
        request.resetState();
        tags.clear();
        user.resetState();

    }
}
