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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments the {@link ConsumerRecords#records(TopicPartition)} method
 */
public class ConsumerRecordsRecordListInstrumentation extends KafkaConsumerRecordsInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("records")
            .and(isPublic())
            .and(takesArgument(0, named("org.apache.kafka.common.TopicPartition")))
            .and(returns(List.class));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ConsumerRecordsAdvice";
    }

    public static class ConsumerRecordsAdvice {

        private static final KafkaInstrumentationHeadersHelper helper = KafkaInstrumentationHeadersHelper.get();

        @Nullable
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static List<ConsumerRecord<?, ?>> wrapRecordList(@Advice.Return @Nullable final List<ConsumerRecord<?, ?>> list) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null || list == null) {
                return list;
            }

            return helper.wrapConsumerRecordList(list);
        }
    }
}
