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
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;

import javax.annotation.Nullable;
import java.util.List;

public class KafkaInstrumentationHelper {

    public static final Logger logger = LoggerFactory.getLogger(KafkaInstrumentationHelper.class);
    private static final KafkaInstrumentationHelper INSTANCE = new KafkaInstrumentationHelper(GlobalTracer.get());

    private final ObjectPool<CallbackWrapper> callbackWrapperObjectPool;
    private final Tracer tracer;
    private final MessagingConfiguration messagingConfiguration;

    public static KafkaInstrumentationHelper get() {
        return INSTANCE;
    }

    public KafkaInstrumentationHelper(Tracer tracer) {
        this.tracer = tracer;
        this.messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
        this.callbackWrapperObjectPool = tracer.getObjectPoolFactory().createRecyclableObjectPool(256,
            new CallbackWrapperAllocator()
        );
    }

    private final class CallbackWrapperAllocator implements Allocator<CallbackWrapper> {
        @Override
        public CallbackWrapper createInstance() {
            return new CallbackWrapper(KafkaInstrumentationHelper.this);
        }
    }

    private boolean ignoreTopic(String topicName) {
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), topicName);
    }

    @Nullable
    public Span<?> onSendStart(ProducerRecord<?, ?> record) {

        String topic = record.topic();
        if (ignoreTopic(topic)) {
            return null;
        }

        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return null;
        }
        final Span<?> span = active.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType("messaging")
            .withSubtype("kafka")
            .withAction("send")
            .withName("KafkaProducer#send to ").appendToName(topic);

        span.getContext().getMessage()
            .withQueue(topic);

        span.getContext().getServiceTarget()
            .withType("kafka")
            .withName(topic);

        span.activate();
        return span;
    }

    @Nullable
    public Callback wrapCallback(@Nullable Callback callback, Span<?> span) {
        if (callback instanceof CallbackWrapper) {
            // don't wrap twice
            return callback;
        }
        try {
            return callbackWrapperObjectPool.createInstance().wrap(callback, span);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka send callback", throwable);
            return callback;
        }
    }

    void recycle(CallbackWrapper callbackWrapper) {
        callbackWrapperObjectPool.recycle(callbackWrapper);
    }

    public void onSendEnd(Span<?> span, ProducerRecord<?, ?> producerRecord, KafkaProducer<?, ?> kafkaProducer, @Nullable Throwable throwable) {

        // Topic address collection is normally very fast, as it uses cached cluster state information. However,
        // when the cluster metadata is required to be updated, its query may block for a short period. In
        // addition, the discovery operation allocates two objects. Therefore, we have the ability to turn it off.
        if (messagingConfiguration.shouldCollectQueueAddress()) {
            try {
                // Better get the destination now, as if the partition's leader was replaced, it may be reflected at
                // this point.
                List<PartitionInfo> partitions = kafkaProducer.partitionsFor(producerRecord.topic());
                Integer partition = producerRecord.partition();
                PartitionInfo partitionInfo = null;
                if (partition != null) {
                    partitionInfo = partitions.get(partition);
                } else if (!partitions.isEmpty()) {
                    // probably not a partitioned topic, so look for the singe entry
                    partitionInfo = partitions.get(0);
                }
                if (partitionInfo != null) {
                    // Records are always sent to the leader of a partition and then synced with replicas internally by
                    // the broker.
                    Node leader = partitionInfo.leader();
                    if (leader != null) {
                        span.getContext().getDestination().withAddress(leader.host()).withPort(leader.port());
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to get Kafka producer's destination", e);
            }
        }

        span.captureException(throwable);

        // Not ending here- ending in the callback
        span.deactivate();
    }
}
