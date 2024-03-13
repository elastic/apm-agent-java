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


import co.elastic.apm.agent.tracer.SpanContext;

/**
 * Any other arbitrary data captured by the agent, optionally provided by the user
 */
public class SpanContextImpl extends AbstractContextImpl implements SpanContext {

    /**
     * An object containing contextual data for database spans
     */
    private final DbImpl db = new DbImpl();

    /**
     * An object containing contextual data for outgoing HTTP spans
     */
    private final HttpImpl http = new HttpImpl();

    /**
     * An object containing contextual data for service maps
     */
    private final DestinationImpl destination = new DestinationImpl();

    /**
     * An object containing contextual data for service target
     */
    private final ServiceTargetImpl serviceTarget = new ServiceTargetImpl();

    @Override
    public DbImpl getDb() {
        return db;
    }

    @Override
    public HttpImpl getHttp() {
        return http;
    }

    @Override
    public DestinationImpl getDestination() {
        return destination;
    }

    @Override
    public ServiceTargetImpl getServiceTarget() {
        return serviceTarget;
    }

    @Override
    public void resetState() {
        super.resetState();
        db.resetState();
        http.resetState();
        destination.resetState();
        serviceTarget.resetState();
    }

    public boolean hasContent() {
        return super.hasContent()
            || db.hasContent()
            || http.hasContent()
            || destination.hasContent()
            || serviceTarget.hasContent();
    }
}
