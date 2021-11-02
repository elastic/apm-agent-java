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


import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SpringAmqpBatchMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessageBatch")
            .and(takesArgument(0, List.class)).and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.rabbitmq.SpringAmqpBatchMessageListenerInstrumentation$MessageListenerContainerWrappingAdvice";
    }

    public static class MessageListenerContainerWrappingAdvice extends BaseAdvice {
        protected static final MessageBatchHelper messageBatchHelper;

        static {
            ElasticApmTracer elasticApmTracer = GlobalTracer.requireTracerImpl();
            messageBatchHelper = new MessageBatchHelper(elasticApmTracer, transactionHelper);
        }

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(0))
        @Advice.OnMethodEnter(inline = false)
        public static List<Message> beforeOnBatch(@Advice.Argument(0) @Nullable final List<Message> messageBatch) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null || messageBatch == null) {
                return messageBatch;
            }
            return messageBatchHelper.wrapMessageBatchList(messageBatch);
        }
    }
}
