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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.metadata.Message;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Map;

public abstract class AbstractBaseInstrumentation extends TracerAwareInstrumentation {

    private static final CoreConfiguration coreConfiguration = GlobalTracer.get().getConfig(CoreConfiguration.class);
    private static final MessagingConfiguration messagingConfiguration = GlobalTracer.get().getConfig(MessagingConfiguration.class);

    /**
     * @param name name of the exchange or queue
     * @return {@literal true} when exchange or queue is ignored, {@literal false otherwise}
     */
    protected static boolean isIgnored(String name) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), name);
    }

    protected static boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }

    protected static boolean captureHeaderKey(String key) {
        return !WildcardMatcher.isAnyMatch(coreConfiguration.getSanitizeFieldNames(), key);
    }

    /**
     * Captures queue name and optional timestamp
     *
     * @param queueOrExchange queue or exchange name to use in message.queue.name
     * @param age             age
     * @param context         span/transaction context
     * @return captured message
     */
    protected static Message captureMessage(String queueOrExchange, @Nullable String routingKey, long age, AbstractSpan<?> context) {
        return context.getContext().getMessage()
            .withQueue(queueOrExchange)
            .withRoutingKey(routingKey)
            .withAge(age);
    }

    protected static long getTimestamp(@Nullable Date timestamp) {
        long age = -1L;
        if (timestamp != null) {
            long now = System.currentTimeMillis();
            long time = timestamp.getTime();
            age = time <= now ? (now - time) : 0;
        }
        return age;
    }

    protected static void captureHeaders(@Nullable Map<String, Object> headers, Message message) {
        if (!isCaptureHeaders() || headers == null || headers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (captureHeaderKey(entry.getKey())) {
                // headers aren't stored as String instances here
                message.addHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
    }

    protected static String normalizeExchangeName(@Nullable String exchange) {
        if (exchange == null) {
            // unlikely but allows to avoid propagating a nullable field
            return "<unknown>";
        } else if (exchange.isEmpty()) {
            return "<default>";
        }
        return exchange;
    }

    protected static String normalizeQueueName(@Nullable String queue) {
        if (queue == null) {
            // unlikely but allows to avoid propagating a nullable field
            return "<unknown>";
        } else if (queue.startsWith("amq.gen-")) {
            // generated queues create high cardinality transaction/span names
            return "amq.gen-*";
        }
        return queue;
    }
}
