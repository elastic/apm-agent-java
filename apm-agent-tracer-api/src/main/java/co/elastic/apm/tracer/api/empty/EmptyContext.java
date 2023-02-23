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
package co.elastic.apm.tracer.api.empty;

import co.elastic.apm.tracer.api.AbstractContext;
import co.elastic.apm.tracer.api.SpanContext;
import co.elastic.apm.tracer.api.TransactionContext;
import co.elastic.apm.tracer.api.metadata.*;
import co.elastic.apm.tracer.api.service.ServiceTarget;

public class EmptyContext implements SpanContext, TransactionContext, AbstractContext {

    public static final EmptyContext INSTANCE = new EmptyContext();

    private EmptyContext() {
    }

    @Override
    public Message getMessage() {
        return EmptyMessage.INSTANCE;
    }

    @Override
    public ServiceTarget getServiceTarget() {
        return EmptyServiceTarget.INSTANCE;
    }

    @Override
    public Destination getDestination() {
        return EmptyDestination.INSTANCE;
    }

    @Override
    public Db getDb() {
        return EmptyDb.INSTANCE;
    }

    @Override
    public Http getHttp() {
        return EmptyHttp.INSTANCE;
    }

    @Override
    public Request getRequest() {
        return EmptyRequest.INSTANCE;
    }

    @Override
    public Response getResponse() {
        return EmptyResponse.INSTANCE;
    }

    @Override
    public User getUser() {
        return EmptyUser.INSTANCE;
    }

    @Override
    public CloudOrigin getCloudOrigin() {
        return EmptyCloudOrigin.INSTANCE;
    }
}
