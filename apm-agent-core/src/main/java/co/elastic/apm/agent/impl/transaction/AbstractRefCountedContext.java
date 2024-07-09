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

import co.elastic.apm.agent.tracer.pooling.Recyclable;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractRefCountedContext<T extends AbstractRefCountedContext<T>> extends TraceStateImpl<T> implements Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRefCountedContext.class);

    private final AtomicInteger references = new AtomicInteger();

    protected AbstractRefCountedContext(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public void incrementReferences() {
        int referenceCount = references.incrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("increment references to {} ({})", this, referenceCount);
            if (logger.isTraceEnabled()) {
                logger.trace("incrementing references at",
                        new RuntimeException("This is an expected exception. Is just used to record where the reference count has been incremented."));
            }
        }
    }

    @Override
    public void decrementReferences() {
        int referenceCount = references.decrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("decrement references to {} ({})", this, referenceCount);
            if (logger.isTraceEnabled()) {
                logger.trace("decrementing references at",
                        new RuntimeException("This is an expected exception. Is just used to record where the reference count has been decremented."));
            }
        }
        if (referenceCount == 0) {
            recycle();
        }
    }

    public boolean isReferenced() {
        return references.get() > 0;
    }

    public int getReferenceCount() {
        return references.get();
    }

    protected abstract void recycle();

    @Override
    public void resetState() {
        references.set(0);
    }
}
