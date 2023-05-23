/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.impl.context;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Context
 * <p>
 * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
 */
public class TransactionContext extends AbstractContext implements co.elastic.apm.agent.tracer.TransactionContext {

    /**
     * A flat mapping of user-defined {@link String} keys and {@link String}, {@link Number} or {@link Boolean} values
     * <p>
     * In contrast to {@link #labels} these are not indexed in Elasticsearch
     * </p>
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
     * User
     * <p>
     * Describes the authenticated User for a request.
     */
    private final User user = new User();

    /**
     * CloudOrigin
     * <p>
     * Describes the cloud information about the origin of a request.
     */
    private final CloudOrigin cloudOrigin = new CloudOrigin();

    /**
     * ServiceOrigin
     * <p>
     * Describes the service information about the origin of a request.
     */
    private final ServiceOrigin serviceOrigin = new ServiceOrigin();

    public void copyFrom(TransactionContext other) {
        super.copyFrom(other);
        response.copyFrom(other.response);
        request.copyFrom(other.request);
        user.copyFrom(other.user);
        cloudOrigin.copyFrom(other.cloudOrigin);
        serviceOrigin.copyFrom(other.serviceOrigin);
    }

    public Object getCustom(String key) {
        return custom.get(key);
    }

    @Override
    public Response getResponse() {
        return response;
    }

    public void addCustom(String key, String value) {
        custom.put(key, value);
    }

    public void addCustom(String key, Number value) {
        custom.put(key, value);
    }

    public void addCustom(String key, boolean value) {
        custom.put(key, value);
    }

    public boolean hasCustom() {
        return !custom.isEmpty();
    }

    public Iterator<? extends Map.Entry<String, ?>> getCustomIterator() {
        return custom.entrySet().iterator();
    }

    @Override
    public Request getRequest() {
        return request;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public CloudOrigin getCloudOrigin() {
        return cloudOrigin;
    }

    public ServiceOrigin getServiceOrigin() {
        return serviceOrigin;
    }

    @Override
    public void resetState() {
        super.resetState();
        custom.clear();
        response.resetState();
        request.resetState();
        user.resetState();
        cloudOrigin.resetState();
        serviceOrigin.resetState();
    }

    public void onTransactionEnd() {
        request.onTransactionEnd();
    }

}
