/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.not;

@SuppressWarnings("rawtypes")
public abstract class BaseKafkaInstrumentation extends TracerAwareInstrumentation {

    @SuppressWarnings({"WeakerAccess"})
    @Nullable
    @VisibleForAdvice
    // Referencing Kafka classes is legal due to type erasure. The field must be public in order for it to be accessible from injected code
    public static HelperClassManager<KafkaInstrumentationHelper<Callback, ProducerRecord, KafkaProducer>> kafkaInstrHelperManager;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @VisibleForAdvice
    public static MessagingConfiguration messagingConfiguration;

    private synchronized static void init(ElasticApmTracer tracer) {
        if (kafkaInstrHelperManager == null) {
            kafkaInstrHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                "co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelperImpl",
                "co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelperImpl$CallbackWrapperAllocator",
                "co.elastic.apm.agent.kafka.helper.CallbackWrapper");
        }
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    BaseKafkaInstrumentation(ElasticApmTracer tracer) {
        BaseKafkaInstrumentation.init(tracer);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("kafka");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("org.apache.kafka.clients.consumer.ConsumerRecord"));
    }

    @Override
    public boolean indyPlugin() {
        return false;
    }
}
