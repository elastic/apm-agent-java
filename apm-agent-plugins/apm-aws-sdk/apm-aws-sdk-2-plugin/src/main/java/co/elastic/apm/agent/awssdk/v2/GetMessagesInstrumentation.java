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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.v2.helper.sqs.wrapper.MessageListWrapper;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.state.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

public class GetMessagesInstrumentation extends ElasticApmInstrumentation {


    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("messages")
            .and(returns(named("java.util.List")))
            .and(takesNoArguments());
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-sdk");
    }

    public static class AdviceClass {
        private static final CallDepth callDepth = CallDepth.get(GetMessagesInstrumentation.AdviceClass.class);

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        @Advice.AssignReturned.ToReturned
        public static List<Message> onExit(@Advice.This ReceiveMessageResponse response,
                                           @Advice.Return List<Message> messagesList) {
            List<Message> returnedList = messagesList;
            if (!callDepth.isNestedCallAndIncrement()) {
                if (!messagesList.isEmpty()) {
                    List<Message> wrappedList = MessageListWrapper.getMessagesList(response);
                    if (wrappedList != null) {
                        returnedList = wrappedList;
                    }
                }
            }
            callDepth.decrement();
            return returnedList;
        }
    }
}
