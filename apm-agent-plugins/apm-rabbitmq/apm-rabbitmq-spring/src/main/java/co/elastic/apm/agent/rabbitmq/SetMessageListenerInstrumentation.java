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

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.MessageListener;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SetMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Container");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setMessageListener").and(takesArgument(0, named("org.springframework.amqp.core.MessageListener")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.rabbitmq.SetMessageListenerInstrumentation$MessageListenerContainerWrappingAdvice";
    }

    public static class MessageListenerContainerWrappingAdvice {

        private static final MessageListenerHelper helper = new MessageListenerHelper();

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(0))
        @Advice.OnMethodEnter(inline = false)
        public static MessageListener beforeSetListener(@Advice.Argument(0) @Nullable MessageListener original) {
            return helper.registerAndWrapLambda(original);
        }
    }
}
