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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class BaseKafkaHeadersInstrumentation extends BaseKafkaInstrumentation {

    @SuppressWarnings({"WeakerAccess", "rawtypes"})
    @Nullable
    @VisibleForAdvice
    // Referencing Kafka classes is legal due to type erasure. The field must be public in order for it to be accessible from injected code
    public static HelperClassManager<KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord>> kafkaInstrHeadersHelperManager;

    private synchronized static void init(ElasticApmTracer tracer) {
        if (kafkaInstrHeadersHelperManager == null) {
            kafkaInstrHeadersHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                "co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelperImpl",
                "co.elastic.apm.agent.kafka.helper.ConsumerRecordsIteratorWrapper",
                "co.elastic.apm.agent.kafka.helper.ConsumerRecordsIterableWrapper",
                "co.elastic.apm.agent.kafka.helper.ConsumerRecordsListWrapper",
                "co.elastic.apm.agent.kafka.helper.ElasticHeaderImpl",
                "co.elastic.apm.agent.kafka.helper.KafkaRecordHeaderAccessor",
                "co.elastic.apm.agent.kafka.helper.KafkaRecordHeaderAccessor$HeaderValuesIterator");
        }
    }

    BaseKafkaHeadersInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
        BaseKafkaHeadersInstrumentation.init(tracer);
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers"));
    }
}
