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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class KafkaProducerInstrumentation extends BaseKafkaInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(not(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers")));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.kafka.clients.producer.KafkaProducer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic().and(named("send"))
            .and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")))
            .and(takesArgument(1, named("org.apache.kafka.clients.producer.Callback")));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$KafkaProducerAdvice";
    }

    public static class KafkaProducerAdvice {

        public static final KafkaInstrumentationHelper helper = KafkaInstrumentationHelper.get();

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(1))
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Callback beforeSend(@Advice.Argument(0) final ProducerRecord<?, ?> record,
                                          @Advice.Argument(1) @Nullable Callback callback) {
            Span<?> span = helper.onSendStart(record);
            if (span == null) {
                return callback;
            }

            return helper.wrapCallback(callback, span);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterSend(@Advice.Argument(0) final ProducerRecord<?, ?> record,
                                     @Advice.This final KafkaProducer<?, ?> thiz,
                                     @Advice.Thrown final Throwable throwable) {
            AbstractSpan<?> active = tracer.getActive();
            if (active instanceof Span<?>) {
                Span<?> activeSpan = (Span<?>) active;
                if (activeSpan.isExit()) {
                    helper.onSendEnd(activeSpan, record, thiz, throwable);
                }
            }
        }
    }
}
