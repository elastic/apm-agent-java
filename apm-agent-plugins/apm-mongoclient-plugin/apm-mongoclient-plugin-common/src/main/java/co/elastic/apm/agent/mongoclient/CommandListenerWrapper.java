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

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Recyclable;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandListenerWrapper implements CommandListener, Recyclable {

    private final Map<Integer, Span> spanMap = new ConcurrentHashMap<>();
    private MongoClientInstrumentationHelper helper;

    @Nullable
    private CommandListener delegate;

    public CommandListenerWrapper(MongoClientInstrumentationHelper helper) {
        this.helper = helper;
    }

    public CommandListenerWrapper with(CommandListener delegate) {
        this.delegate = delegate;
        return this;
    }

    @Override
    public void commandStarted(final CommandStartedEvent event) {
        System.out.println("#Started");
        try {
            Span span = helper.createClientSpan(event);
            this.spanMap.put(event.getRequestId(), span);
        } finally {
            if (delegate != null) {
                delegate.commandStarted(event);
            }
        }
    }

    @Override
    public void commandSucceeded(final CommandSucceededEvent event) {
        System.out.println("#Finished");
        final Span span = spanMap.remove(event.getRequestId());
        try {
            helper.finishClientSpan(event, span, null);
        } finally {
            if (delegate != null) {
                delegate.commandSucceeded(event);
            }
        }
    }

    @Override
    public void commandFailed(final CommandFailedEvent event) {
        System.out.println("#Failed");
        final Span span = spanMap.remove(event.getRequestId());
        try {
            helper.finishClientSpan(event, span, event.getThrowable());
        } finally {
            if (delegate != null) {
                delegate.commandFailed(event);
            }
        }
    }

    @Override
    public void resetState() {
        System.out.println("### RESET STATE");
        delegate = null;
    }
}
