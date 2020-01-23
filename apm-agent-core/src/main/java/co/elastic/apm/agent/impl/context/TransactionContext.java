/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
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
public class TransactionContext extends AbstractContext {

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

    public void copyFrom(TransactionContext other) {
        super.copyFrom(other);
        response.copyFrom(other.response);
        request.copyFrom(other.request);
        user.copyFrom(other.user);
    }

    public Object getCustom(String key) {
        return custom.get(key);
    }

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

    /**
     * Request
     * <p>
     * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
     */
    public Request getRequest() {
        return request;
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
        super.resetState();
        custom.clear();
        response.resetState();
        request.resetState();
        user.resetState();
    }

    public void onTransactionEnd() {
        request.onTransactionEnd();
    }
}
