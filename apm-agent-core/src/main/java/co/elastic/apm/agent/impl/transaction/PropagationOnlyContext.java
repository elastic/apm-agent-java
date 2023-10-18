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

import javax.annotation.Nullable;

public class PropagationOnlyContext extends AbstractRefCountedContext<PropagationOnlyContext> {

    private Baggage baggage;

    /**
     * This holds the remote trace context to be propagated, if available.
     * If this context was created without a remote trace context, a new root trace context with default sampling is used instead.
     */
    private final TraceContext remoteTraceParent;

    public PropagationOnlyContext(ElasticApmTracer tracer) {
        super(tracer);
        remoteTraceParent = TraceContext.with64BitId(tracer);
        baggage = Baggage.EMPTY;
    }

    public <C> void initFrom(C carrier, HeaderGetter<?, C> getter) {
        if(remoteTraceParent.asChildOf(carrier, getter)) {
            remoteTraceParent.replaceWithParent();
        } else {
            //Create a dummy remote-parent
            remoteTraceParent.asRootSpan(tracer.getSampler());
        }
        Baggage.Builder baggageBuilder = Baggage.builder();
        W3CBaggagePropagation.parse(carrier, getter, baggageBuilder);
        baggage = baggageBuilder.build();
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
    public void resetState() {
        super.resetState();
        baggage = Baggage.EMPTY;
        remoteTraceParent.resetState();
    }

    @Override
    protected void recycle() {
        tracer.recycle(this);
    }

    @Override
    public String toString() {
        return String.format("RemoteParentContext %s (%s)", remoteTraceParent, Integer.toHexString(System.identityHashCode(this)));
    }
}
