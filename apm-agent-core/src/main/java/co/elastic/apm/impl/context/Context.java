/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Context
 * <p>
 * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
 */
public class Context implements Recyclable {

    /**
     * An arbitrary mapping of additional metadata to store with the event.
     */
    private final Map<String, Object> custom = new ConcurrentHashMap<>();
    private final Response response = new Response();
    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    private final Request request = new Request();
    /**
     * A flat mapping of user-defined tags with string values.
     */
    private final Map<String, String> tags = new ConcurrentHashMap<>();
    /**
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
    private final User user = new User();


    public void copyFrom(Context other) {
        response.copyFrom(other.response);
        request.copyFrom(other.request);
        user.copyFrom(other.user);
    }

    /**
     * An arbitrary mapping of additional metadata to store with the event.
     */
    public Map<String, Object> getCustom() {
        return custom;
    }

    public Response getResponse() {
        return response;
    }

    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    public Request getRequest() {
        return request;
    }

    /**
     * A flat mapping of user-defined tags with string values.
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
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
