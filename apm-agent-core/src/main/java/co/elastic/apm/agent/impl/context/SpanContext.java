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


/**
 * Any other arbitrary data captured by the agent, optionally provided by the user
 */
public class SpanContext extends AbstractContext implements co.elastic.apm.agent.tracer.SpanContext {

    /**
     * An object containing contextual data for database spans
     */
    private final Db db = new Db();

    /**
     * An object containing contextual data for outgoing HTTP spans
     */
    private final Http http = new Http();

    /**
     * An object containing contextual data for service maps
     */
    private final Destination destination = new Destination();

    /**
     * An object containing contextual data for service target
     */
    private final ServiceTarget serviceTarget = new ServiceTarget();

    @Override
    public Db getDb() {
        return db;
    }

    @Override
    public Http getHttp() {
        return http;
    }

    @Override
    public Destination getDestination() {
        return destination;
    }

    @Override
    public ServiceTarget getServiceTarget() {
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
