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
import java.util.concurrent.TimeUnit;

public abstract class AbstractSpan<T extends AbstractSpan> implements Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpan.class);
    protected static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    protected final TraceContext traceContext = TraceContext.with64BitId();
    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    protected final ElasticApmTracer tracer;
    protected long timestamp;
    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    protected double duration;
    /**
     * Keyword of specific relevance in the service's domain
     * (eg:  'request', 'backgroundjob' for transactions and
     * 'db.postgresql.query', 'template.erb', etc for spans)
     * (Required)
     */
    @Nullable
    private volatile String type;
    private volatile boolean finished = true;

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
     * Recorded time of the span or transaction in microseconds since epoch
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
        finished = true;
        name.setLength(0);
        timestamp = 0;
        duration = 0;
        type = null;
        traceContext.resetState();
        // don't reset previouslyActive, as deactivate can be called after end
    }

    public boolean isChildOf(AbstractSpan<?> parent) {
        return traceContext.isChildOf(parent.traceContext);
    }

    public T activate() {
        tracer.activate(this);
        List<SpanListener> spanListeners = tracer.getSpanListeners();
        for (int i = 0; i < spanListeners.size(); i++) {
            try {
                spanListeners.get(i).onActivate(this);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#onActivate", spanListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        return (T) this;
    }

    public T deactivate() {
        tracer.deactivate(this);
        List<SpanListener> spanListeners = tracer.getSpanListeners();
        for (int i = 0; i < spanListeners.size(); i++) {
            try {
                spanListeners.get(i).onDeactivate(this);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#onDeactivate", spanListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        return (T) this;
    }

    public Scope activateInScope() {
        // already in scope
        if (tracer.activeSpan() == this) {
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
        return createSpan(traceContext.getClock().getEpochMicros());
    }

    public Span createSpan(long epochMicros) {
        return tracer.startSpan(this, epochMicros);
    }

    public abstract void addTag(String key, String value);

    protected void onStart() {
        this.finished = false;
    }

    public void end() {
        end(traceContext.getClock().getEpochMicros());
    }

    public final void end(long epochMicros) {
        if (!finished) {
            this.finished = true;
            this.duration = (epochMicros - timestamp) / AbstractSpan.MS_IN_MICROS;
            if (type == null) {
                type = "custom";
            }
            if (name.length() == 0) {
                name.append("unnamed");
            }
            doEnd(epochMicros);
        } else {
            logger.warn("End has already been called: {}" + this);
            assert false;
        }
    }

    protected abstract void doEnd(long epochMicros);

    /**
     * @return Keyword of specific relevance in the service's domain
     * (eg:  'request', 'backgroundjob' for transactions and
     * 'db.postgresql.query', 'template.erb', etc for spans)
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain
     * (eg:  'request', 'backgroundjob' for transactions and
     * 'db.postgresql.query', 'template.erb', etc for spans)
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
            captureException(clock.getEpochMicros(), t);
        }
        return (T) this;
    }

    public void captureException(long epochMicros, Throwable t) {
        tracer.captureException(epochMicros, t, this);
    }
}
