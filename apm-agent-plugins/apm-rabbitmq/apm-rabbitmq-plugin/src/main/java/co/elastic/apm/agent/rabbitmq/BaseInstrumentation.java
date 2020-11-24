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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import com.rabbitmq.client.AMQP;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public abstract class BaseInstrumentation extends TracerAwareInstrumentation {

    private static CoreConfiguration coreConfiguration;
    private static MessagingConfiguration messagingConfiguration;

    public BaseInstrumentation(ElasticApmTracer tracer) {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("rabbitmq");
    }

    /**
     * @param name name of the exchange or queue
     * @return {@literal true} when exchange or queue is ignored, {@literal false otherwise}
     */
    protected static boolean isIgnored(String name) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), name);
    }

    private static boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }

    private static boolean captureHeaderKey(String key) {
        return !WildcardMatcher.isAnyMatch(coreConfiguration.getSanitizeFieldNames(), key);
    }

    protected static Message captureMessage(String exchange, @Nullable AMQP.BasicProperties properties, AbstractSpan<?> context) {
        return context.getContext().getMessage()
            .withQueue(exchange)
            .withAge(getTimestamp(properties));
    }

    protected static String normalizeExchangeName(@Nullable String exchange) {
        if (exchange == null) {
            return "<unknown>";
        } else if (exchange.isEmpty()) {
            return "<default>";
        }
        return exchange;
    }

    protected static String normalizeQueueOrRoutingKey(@Nullable String name) {
        if (name == null) {
            return "<unknown>";
        } else if (name.startsWith("amq.gen-")) {
            // generated routing keys & queues create high cardinality transaction/span names
            return "amq.gen-*";
        }
        return name;
    }

    private static long getTimestamp(@Nullable AMQP.BasicProperties properties) {
        long age = -1L;
        if (null != properties) {

            Date timestamp = properties.getTimestamp();
            if (timestamp != null) {
                long now = System.currentTimeMillis();
                long time = timestamp.getTime();
                age = time <= now ? (now - time) : 0;
            }
        }
        return age;
    }

    protected static void captureHeaders(@Nullable AMQP.BasicProperties properties, Message message) {
        Map<String, Object> headers = properties != null ? properties.getHeaders() : null;
        if (!isCaptureHeaders() || headers == null || headers.size() <= 0) {
            return;
        }

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (captureHeaderKey(entry.getKey())) {
                // headers aren't stored as String instances here
                message.addHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

    }
}
