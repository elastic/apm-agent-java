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
package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.tracer.BaggageContextBuilder;

import javax.annotation.Nullable;

public class BaggageContext extends ElasticContext<BaggageContext> {

    @Nullable
    private final AbstractSpan<?> span;
    private final Baggage baggage;

    private BaggageContext(ElasticContext<?> parent, Baggage baggage) {
        super(parent.getTracer());
        this.span = parent.getSpan();
        this.baggage = baggage;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getSpan() {
        return span;
    }

    @Override
    public Baggage getBaggage() {
        return baggage;
    }

    @Nullable
    @Override
    public Span createSpan() {
        if (span != null) {
            return span.createSpan(baggage);
        }
        return null;
    }

    @Override
    public void incrementReferences() {
        if (span != null) {
            span.incrementReferences();
        }
    }

    @Override
    public void decrementReferences() {
        if (span != null) {
            span.decrementReferences();
        }
    }

    public static BaggageContext.Builder createBuilder(ElasticContext<?> parent) {
        return new Builder(parent);
    }

    public static class Builder implements BaggageContextBuilder {

        private final ElasticContext<?> parent;
        private final Baggage.Builder baggageBuilder;

        public Builder(ElasticContext<?> parent) {
            this.parent = parent;
            this.baggageBuilder = parent.getBaggage().toBuilder();
        }

        @Override
        public Builder put(String key, @Nullable String value) {
            baggageBuilder.put(key, value);
            return this;
        }

        @Override
        public Builder remove(String key) {
            baggageBuilder.put(key, null);
            return this;
        }

        @Override
        public BaggageContext buildContext() {
            return new BaggageContext(parent, baggageBuilder.build());
        }
    }
}
