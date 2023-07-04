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
package co.elastic.apm.agent.jms.jakarta;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class JmsMessageConsumerInstrumentation extends BaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JmsMessageConsumerInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return getConsumerPreFilterTypeMatcher();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("jakarta.jms.MessageConsumer")));
    }

    public static class ReceiveInstrumentation extends JmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return getReceiverMethodMatcher();
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.jakarta.JmsMessageConsumerInstrumentation$ReceiveInstrumentation$MessageConsumerAdvice";
        }

        public static class MessageConsumerAdvice extends JakartaBaseAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            @Nullable
            public static Object beforeReceive(@Advice.Origin Class<?> clazz,
                                               @Advice.Origin("#m") String methodName) {
                return helper.baseBeforeReceive(clazz, methodName);
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
            public static void afterReceive(@Advice.Origin Class<?> clazz,
                                            @Advice.Origin("#m") String methodName,
                                            @Advice.Enter @Nullable final Object abstractSpanObj,
                                            @Advice.Return @Nullable final Message message,
                                            @Advice.Thrown @Nullable final Throwable throwable) {
                helper.baseAfterReceive(clazz, methodName, abstractSpanObj, message, throwable);
            }
        }
    }

    public static class SetMessageListenerInstrumentation extends JmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setMessageListener").and(takesArgument(0, named("jakarta.jms.MessageListener")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.jakarta.JmsMessageConsumerInstrumentation$SetMessageListenerInstrumentation$ListenerWrappingAdvice";
        }

        public static class ListenerWrappingAdvice extends JakartaBaseAdvice {

            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(inline = false)
            public static MessageListener beforeSetListener(@Advice.Argument(0) @Nullable MessageListener original) {
                return helper.wrapLambda(original);
            }
        }
    }
}
