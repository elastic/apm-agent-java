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
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;

public abstract class AbstractSpan implements Recyclable {
    protected final TraceContext traceContext = new TraceContext();
    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    /**
     * Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    protected long timestamp;
    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    protected double duration;

    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    public StringBuilder getName() {
        return name;
    }

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    public void setName(@Nullable String name) {
        if (!isSampled()) {
            return;
        }
        this.name.setLength(0);
        this.name.append(name);
    }

    /**
     * Recorded time of the transaction, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Transactions that are 'sampled' will include all available information.
     * Transactions that are not sampled will not have 'spans' or 'context'.
     * Defaults to true.
     */
    public boolean isSampled() {
        return traceContext.isSampled();
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    @Override
    public void resetState() {
        name.setLength(0);
        timestamp = 0;
        duration = 0;
    }

    public boolean isChildOf(AbstractSpan parent) {
        return traceContext.isChildOf(parent.traceContext);
    }
}
