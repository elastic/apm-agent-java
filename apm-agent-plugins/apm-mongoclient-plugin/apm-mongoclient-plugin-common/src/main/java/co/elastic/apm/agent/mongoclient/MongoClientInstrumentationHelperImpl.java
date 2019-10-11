/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonValue;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.annotation.Nullable;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class MongoClientInstrumentationHelperImpl implements MongoClientInstrumentationHelper<CommandEvent, CommandListener> {

    private static final String SPAN_TYPE = "db";
    private static final String MONGO = "mongodb";
    private static final String SPAN_ACTION = "query";
    private static final int MAX_POOLED_ELEMENTS = 256;
    private final ElasticApmTracer tracer;

    private final ObjectPool<CommandListenerWrapper> commandListenerWrapperObjectPool;

    public MongoClientInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        commandListenerWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<CommandListenerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new CommandListenerAllocator()
        );
    }

    private class CommandListenerAllocator implements Allocator<CommandListenerWrapper> {

        @Override
        public CommandListenerWrapper createInstance() {
            return new CommandListenerWrapper(MongoClientInstrumentationHelperImpl.this);
        }
    }

    @Nullable
    @Override
    public Span createClientSpan(CommandEvent commandEvent) {
        CommandStartedEvent startedEvent;
        if (commandEvent instanceof CommandStartedEvent) {
            startedEvent = (CommandStartedEvent) commandEvent;
        } else {
            return null;
        }

        final TraceContextHolder<?> activeSpan = tracer.getActive();
        if (activeSpan == null || !activeSpan.isSampled()) {
            return null;
        }

        Span span = activeSpan.createExitSpan();

        if (span == null) {
            return null;
        }

        span.withType(SPAN_TYPE)
            .withSubtype(MONGO)
            .withAction(SPAN_ACTION)
            .appendToName(startedEvent.getDatabaseName()).appendToName(".").appendToName(startedEvent.getCommandName());
        computeName(span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT), startedEvent);
        span.getContext().getDb().withType(MONGO);
        return span;
    }

    private static void computeName(@Nullable StringBuilder name, CommandStartedEvent startedEvent) {
        if (name != null) {
            BsonValue collection = startedEvent.getCommand().get(startedEvent.getCommandName());
            name.append(startedEvent.getDatabaseName());
            if (collection != null) {
                name.append('.')
                    .append(collection.asString().getValue());
            }
            name.append('.')
                .append(startedEvent.getCommandName());
        }
    }

    @Override
    public void finishClientSpan(@Nullable CommandEvent event, Span span, @Nullable Throwable t) {
        try {
            if (span == null) {
                if (t != null) {
                    tracer.captureException(t, getClass().getClassLoader());
                }
            }
        } finally {
            if (span != null) {
                span.captureException(t).end();
            }
        }
    }

    @Override
    public CommandListener wrapCommandListener(CommandListener listener) {
        return commandListenerWrapperObjectPool.createInstance().with(listener);
    }

    void recycle(CommandListenerWrapper listenerWrapper) {
        commandListenerWrapperObjectPool.recycle(listenerWrapper);
    }
}
