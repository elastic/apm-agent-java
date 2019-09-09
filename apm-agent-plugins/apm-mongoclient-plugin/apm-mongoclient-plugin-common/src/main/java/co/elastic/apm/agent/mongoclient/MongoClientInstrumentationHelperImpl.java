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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class MongoClientInstrumentationHelperImpl implements MongoClientInstrumentationHelper<CommandEvent, CommandListener> {

    private static final List<String> COMMANDS = Arrays.asList("insert", "count", "find", "create");

    private static final BsonValue PARAM_CHAR = new BsonString("?");

    public static final String SPAN_TYPE = "db";
    public static final String MONGO = "mongo";
    public static final String SPAN_ACTION = "request";
    private static final int MAX_POOLED_ELEMENTS = 256;
    private final ElasticApmTracer tracer;

    private final ObjectPool<CommandListenerWrapper> commandListenerWrapperObjectPool;

    public MongoClientInstrumentationHelperImpl(ElasticApmTracer tracer) {
        System.out.println("Construct MongoClientInstrumentationHelperImpl");
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
            System.out.println("### Try to create wrapper instance.");
            return new CommandListenerWrapper(MongoClientInstrumentationHelperImpl.this);
        }
    }

    @Override
    public Span createClientSpan(CommandEvent commandEvent) {
        System.out.println("### Try to create client span.");
        if (commandEvent instanceof CommandStartedEvent) {
            CommandStartedEvent startedEvent = (CommandStartedEvent) commandEvent;
            final TraceContextHolder<?> activeSpan = tracer.getActive();
            if (activeSpan == null || !activeSpan.isSampled()) {
                System.out.println("### Is null when create span.");
                return null;
            }

            Span span = activeSpan.createExitSpan();

            if (span == null) {
                return null;
            }

            span.withType(SPAN_TYPE)
                .withSubtype(MONGO)
                .withAction(SPAN_ACTION)
                .appendToName(parseCommand(startedEvent.getCommand()));
            span.getContext().getDb().withType(MONGO);
            span.activate();

            return span;
        }
        return null;
    }

    @Override
    public void finishClientSpan(@Nullable CommandEvent event, Span span, @Nullable Throwable t) {
        System.out.println("### Try to finish client span via helper.");
        try {
            if (span == null) {
                System.out.println("### Captured exception to current transaction");
                tracer.captureException(t, getClass().getClassLoader());
            }
        } finally {
            if (span != null) {
                System.out.println("### is not null. try to end. =" + span.getNameAsString());
                span.captureException(t).deactivate().end();
            }
        }
    }

    @Override
    public CommandListener wrapCommandListener(CommandListener listener, Span span) {
        return commandListenerWrapperObjectPool.createInstance().with(listener);
    }

    void recycle(CommandListenerWrapper listenerWrapper) {
        commandListenerWrapperObjectPool.recycle(listenerWrapper);
    }

    public static String parseCommand(final BsonDocument statement) {
        final BsonDocument parsed = parse(statement);
        return parsed.toString();
    }

    private static BsonDocument parse(final BsonDocument origin) {
        final BsonDocument transformed = new BsonDocument();
        for (final Map.Entry<String, BsonValue> entry : origin.entrySet()) {
            if (COMMANDS.contains(entry.getKey()) && entry.getValue().isString()) {
                transformed.put(entry.getKey(), entry.getValue());
            } else {
                final BsonValue child = parse(entry.getValue());
                transformed.put(entry.getKey(), child);
            }
        }
        return transformed;
    }

    private static BsonValue parse(final BsonValue origin) {
        final BsonValue value;
        if (origin.isDocument()) {
            value = parse(origin.asDocument());
        } else if (origin.isArray()) {
            value = parse(origin.asArray());
        } else {
            value = PARAM_CHAR;
        }
        return value;
    }

    private static BsonValue parse(final BsonArray origin) {
        final BsonArray array = new BsonArray();
        for (final BsonValue value : origin) {
            final BsonValue child = parse(value);
            array.add(child);
        }
        return array;
    }
}
