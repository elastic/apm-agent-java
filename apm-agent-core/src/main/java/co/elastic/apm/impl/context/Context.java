/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
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
