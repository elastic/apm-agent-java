package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


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
    private final Map<String, Object> custom = new ConcurrentHashMap<>();
    @JsonProperty("response")
    private final Response response = new Response();
    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    @JsonProperty("request")
    private final Request request = new Request();
    /**
     * A flat mapping of user-defined tags with string values.
     */
    @JsonProperty("tags")
    private final Map<String, String> tags = new ConcurrentHashMap<>();
    /**
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
    @JsonProperty("user")
    private final User user = new User();


    public void copyFrom(Context other) {
        response.copyFrom(other.response);
        request.copyFrom(other.request);
        user.copyFrom(other.user);
    }

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
    public void resetState() {
        custom.clear();
        response.resetState();
        request.resetState();
        tags.clear();
        user.resetState();

    }
}
