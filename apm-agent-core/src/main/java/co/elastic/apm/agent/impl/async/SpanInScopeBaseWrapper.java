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
package co.elastic.apm.agent.impl.async;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("WeakerAccess")
abstract class SpanInScopeBaseWrapper {
    private static final Logger logger = LoggerFactory.getLogger(SpanInScopeCallableWrapper.class);
    protected final ElasticApmTracer tracer;

    protected SpanInScopeBaseWrapper(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    protected boolean beforeDelegation(final AbstractSpan<?> localSpan) {
        boolean activated = false;
        if (localSpan != null) {
            try {
                if (tracer.getActive() != localSpan) {
                    // activate only if the corresponding span is not already activated on this thread
                    localSpan.activate();
                    activated = true;
                }
            } catch (Throwable t) {
                try {
                    logger.error("Unexpected error while activating span", t);
                } catch (Throwable ignore) {
                }
            }
        }
        return activated;
    }

    protected void afterDelegation(final AbstractSpan<?> localSpan, boolean activated) {
        try {
            if (localSpan != null) {
                if (activated) {
                    localSpan.deactivate();
                }
                localSpan.decrementReferences();
            }
            doRecycle();
        } catch (Throwable t) {
            try {
                logger.error("Unexpected error while deactivating or recycling span", t);
            } catch (Throwable ignore) {
            }
        }
    }

    protected abstract void doRecycle();
}
