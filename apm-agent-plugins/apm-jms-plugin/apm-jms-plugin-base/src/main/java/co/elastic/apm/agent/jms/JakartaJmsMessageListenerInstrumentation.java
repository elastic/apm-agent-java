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
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import jakarta.jms.Message;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JakartaJmsMessageListenerInstrumentation extends JakartaBaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JakartaJmsMessageListenerInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("jakarta.jms.MessageListener")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessage")
            .and(takesArgument(0, hasSuperType(named("jakarta.jms.Message")))).and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.jms.JakartaJmsMessageListenerInstrumentation$MessageListenerAdvice";
    }

    public static class MessageListenerAdvice extends JakartaBaseAdvice {

        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object beforeOnMessage(@Advice.Argument(0) @Nullable final Message message,
                                             @Advice.Origin Class<?> clazz) {
            return helper.baseBeforeOnMessage(message, clazz);
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterOnMessage(@Advice.Enter @Nullable final Object transactionObj,
                                          @Advice.Thrown final Throwable throwable) {
            helper.deactivateTransaction(transactionObj, throwable);
        }
    }
}
