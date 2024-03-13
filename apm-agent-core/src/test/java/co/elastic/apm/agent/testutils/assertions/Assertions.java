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
package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.context.DbImpl;
import co.elastic.apm.agent.impl.context.DestinationImpl;
import co.elastic.apm.agent.impl.context.ServiceTargetImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.testutils.assertions.metrics.MetricSetsAssert;

import java.util.Collection;

public class Assertions extends org.assertj.core.api.Assertions {

    private Assertions() {
    }

    public static ServiceTargetAssert assertThat(ServiceTargetImpl serviceTarget) {
        return new ServiceTargetAssert(serviceTarget);
    }

    public static DestinationAssert assertThat(DestinationImpl destination) {
        return new DestinationAssert(destination);
    }

    public static SpanAssert assertThat(SpanImpl span) {
        return new SpanAssert(span);
    }

    public static DbAssert assertThat(DbImpl db) {
        return new DbAssert(db);
    }

    public static AbstractSpanAssert<?, ?> assertThat(AbstractSpanImpl<?> span) {
        return new AbstractSpanAssert<>(span, AbstractSpanAssert.class);
    }

    public static ElasticContextAssert<?, ?> assertThat(TraceStateImpl<?> span) {
        return new ElasticContextAssert<>(span, ElasticContextAssert.class);
    }

    public static MetricSetsAssert assertThatMetricSets(Collection<byte[]> metricsetsJson) {
        return new MetricSetsAssert(metricsetsJson);
    }

    public static BaggageAssert assertThat(BaggageImpl baggage) {
        return new BaggageAssert(baggage);
    }
}
