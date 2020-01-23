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
package co.elastic.apm.agent.impl.sampling;

import co.elastic.apm.agent.impl.transaction.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbabilitySamplerTest {

    private static final int ITERATIONS = 1_000_000;
    private static final int DELTA = (int) (ITERATIONS * 0.01);
    private static final double SAMPLING_RATE = 0.5;
    private Sampler sampler;

    @BeforeEach
    void setUp() {
        sampler = ProbabilitySampler.of(SAMPLING_RATE);
    }

    @Test
    void isSampledEmpiricalTest() {
        int sampledTransactions = 0;
        Id id = Id.new128BitId();
        for (int i = 0; i < ITERATIONS; i++) {
            id.setToRandomValue();
            if (sampler.isSampled(id)) {
                sampledTransactions++;
            }
        }
        assertThat(sampledTransactions).isBetween((int) (SAMPLING_RATE * ITERATIONS - DELTA), (int) (SAMPLING_RATE * ITERATIONS + DELTA));
    }

    @Test
    void testSamplingUpperBoundary() {
        long upperBound = Long.MAX_VALUE / 2;
        final Id transactionId = Id.new128BitId();

        transactionId.fromLongs((long) 0, upperBound - 1);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.fromLongs((long) 0, upperBound);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.fromLongs((long) 0, upperBound + 1);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isFalse();
    }

    @Test
    void testSamplingLowerBoundary() {
        long lowerBound = -Long.MAX_VALUE / 2;
        final Id transactionId = Id.new128BitId();

        transactionId.fromLongs((long) 0, lowerBound + 1);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.fromLongs((long) 0, lowerBound);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.fromLongs((long) 0, lowerBound - 1);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isFalse();
    }

}
