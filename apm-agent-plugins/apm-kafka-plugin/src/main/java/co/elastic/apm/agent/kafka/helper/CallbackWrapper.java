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
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import javax.annotation.Nullable;

class CallbackWrapper implements Callback, Recyclable {

    private final KafkaInstrumentationHelperImpl helper;

    @Nullable
    private Callback delegate;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private volatile Span span;

    CallbackWrapper(KafkaInstrumentationHelperImpl helper) {
        this.helper = helper;
    }

    Callback wrap(@Nullable Callback delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
        return this;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        try {
            span.activate();
            if (delegate != null) {
                delegate.onCompletion(metadata, exception);
            }
        } finally {
            span.captureException(exception);
            span.deactivate().end();
            helper.recycle(this);
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        //noinspection ConstantConditions
        span = null;
    }
}
