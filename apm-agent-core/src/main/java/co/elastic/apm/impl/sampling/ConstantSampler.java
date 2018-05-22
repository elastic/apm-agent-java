/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.sampling;

import co.elastic.apm.impl.transaction.TraceId;

/**
 * This is a implementation of {@link Sampler} which always returns the same sampling decision.
 */
public class ConstantSampler implements Sampler {

    private static final Sampler TRUE = new ConstantSampler(true);
    private static final Sampler FALSE = new ConstantSampler(false);

    private final boolean decision;

    private ConstantSampler(boolean decision) {
        this.decision = decision;
    }

    public static Sampler of(boolean decision) {
        if (decision) {
            return TRUE;
        } else {
            return FALSE;
        }
    }

    @Override
    public boolean isSampled(TraceId traceId) {
        return decision;
    }
}
