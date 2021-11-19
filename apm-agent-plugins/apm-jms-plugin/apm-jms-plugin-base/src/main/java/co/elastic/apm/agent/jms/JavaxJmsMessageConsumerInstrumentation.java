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
package co.elastic.apm.agent.jms;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageListener;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class JavaxJmsMessageConsumerInstrumentation extends JavaxBaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JavaxJmsMessageConsumerInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Message")
            .or(nameContains("Consumer"))
            .or(nameContains("Receiver"))
            .or(nameContains("Subscriber"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.jms.MessageConsumer")));
    }

    public static class ReceiveInstrumentation extends JavaxJmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic())
                .or(named("receiveNoWait").and(takesArguments(0).and(isPublic())));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JavaxJmsMessageConsumerInstrumentation$ReceiveInstrumentation$MessageConsumerAdvice";
        }

        public static class MessageConsumerAdvice extends JavaxBaseAdvice {

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

    public static class SetMessageListenerInstrumentation extends JavaxJmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setMessageListener").and(takesArgument(0, named("javax.jms.MessageListener")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JavaxJmsMessageConsumerInstrumentation$SetMessageListenerInstrumentation$ListenerWrappingAdvice";
        }

        public static class ListenerWrappingAdvice extends JavaxBaseAdvice {

            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(inline = false)
            public static MessageListener beforeSetListener(@Advice.Argument(0) @Nullable MessageListener original) {
                return helper.wrapLambda(original);
            }
        }
    }
}
