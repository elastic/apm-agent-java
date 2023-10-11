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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.baggage.Baggage;
import co.elastic.apm.agent.impl.baggage.W3CBaggagePropagation;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

public class RemoteParentContext extends ElasticContext<RemoteParentContext> implements Recyclable {

    private Baggage baggage;

    private final TraceContext remoteTraceParent;

    public RemoteParentContext(ElasticApmTracer tracer) {
        super(tracer);
        remoteTraceParent = TraceContext.with64BitId(tracer);
        baggage = Baggage.EMPTY;
    }

    public <C> boolean fill(C carrier, HeaderGetter<?, C> getter) {
        if(remoteTraceParent.asChildOf(carrier, getter)) {
            remoteTraceParent.replaceWithParent();
        }
        Baggage.Builder baggageBuilder = Baggage.builder();
        W3CBaggagePropagation.parse(carrier, getter, baggageBuilder);
        baggage = Baggage.builder().build();
        return !isEmpty();
    }

    @Nullable
    @Override
    public AbstractSpan<?> getSpan() {
        //The remote parent will never be active when there is an active span or transaction
        return null;
    }

    @Nullable
    @Override
    public TraceContext getRemoteParent() {
        if(remoteTraceParent.hasContent()) {
            return remoteTraceParent;
        }
        return null;
    }

    @Override
    public Baggage getBaggage() {
        return baggage;
    }

    @Override
    public void incrementReferences() {

    }

    @Override
    public void decrementReferences() {

    }

    @Override
    public void resetState() {
        baggage = Baggage.EMPTY;
        remoteTraceParent.resetState();
    }
}
