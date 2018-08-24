/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.Scope;
import co.elastic.apm.impl.SpanListener;
import co.elastic.apm.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractSpan<T extends AbstractSpan> implements Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpan.class);
    protected final TraceContext traceContext = new TraceContext();
    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    protected final ElasticApmTracer tracer;
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
    @Nullable
    private volatile AbstractSpan<?> previouslyActive;
    /**
     * Keyword of specific relevance in the service's domain
     * (eg:  'request', 'backgroundjob' for transactions and
     * 'db.postgresql.query', 'template.erb', etc for spans)
     * (Required)
     */
    @Nullable
    private volatile String type;

    public AbstractSpan(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

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
     * Appends a string to the name.
     * <p>
     * This method helps to avoid the memory allocations of string concatenations
     * as the underlying {@link StringBuilder} instance will be reused.
     * </p>
     *
     * @param s the string to append to the name
     * @return {@code this}, for chaining
     */
    public T appendToName(String s) {
        name.append(s);
        return (T) this;
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
        type = null;
        // don't reset previouslyActive, as deactivate can be called after end
    }

    public boolean isChildOf(AbstractSpan<?> parent) {
        return traceContext.isChildOf(parent.traceContext);
    }

    @Nullable
    public abstract Transaction getTransaction();

    public T activate() {
        final ElasticApmTracer tracer = this.tracer;
        previouslyActive = tracer.getActive();
        tracer.setActive(this);
        List<SpanListener> spanListeners = tracer.getSpanListeners();
        for (int i = 0; i < spanListeners.size(); i++) {
            try {
                spanListeners.get(i).onActivate(this);
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#onActivate", spanListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        return (T) this;
    }

    public T deactivate() {
        final ElasticApmTracer tracer = this.tracer;
        tracer.setActive(previouslyActive);
        List<SpanListener> spanListeners = tracer.getSpanListeners();
        for (int i = 0; i < spanListeners.size(); i++) {
            try {
                spanListeners.get(i).onDeactivate(this);
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#onDeactivate", spanListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        return (T) this;
    }

    public Scope activateInScope() {
        final ElasticApmTracer tracer = this.tracer;
        // already in scope
        if (tracer.currentTransaction() == this) {
            return Scope.NoopScope.INSTANCE;
        }
        activate();
        return new Scope() {
            @Override
            public void close() {
                deactivate();
            }
        };
    }

    public Span createSpan() {
        return createSpan(System.nanoTime());
    }

    public Span createSpan(long startTimeNanos) {
        return tracer.startSpan(this, startTimeNanos);
    }

    public abstract void addTag(String key, String value);

    public abstract void end();

    public abstract void end(long nanoTime);

    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain
     * (eg:  'request', 'backgroundjob' for transactions and
     * 'db.postgresql.query', 'template.erb', etc for spans)
     * (Required)
     */
    public T withType(@Nullable String type) {
        if (!isSampled()) {
            return (T) this;
        }
        this.type = type;
        return (T) this;
    }

    public T captureException(@Nullable Throwable t) {
        if (t != null) {
            captureException(System.currentTimeMillis(), t);
        }
        return (T) this;
    }

    public void captureException(long epochTimestampMillis, Throwable t) {
        tracer.captureException(epochTimestampMillis, t, this);
    }
}
