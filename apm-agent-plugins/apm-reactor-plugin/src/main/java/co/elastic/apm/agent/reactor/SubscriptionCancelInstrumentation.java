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
package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SubscriptionCancelInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("reactor.core.CoreSubscriber");
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Subscriber").or(nameContains("Subscription"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(declaresMethod(getMethodMatcher()))
            .and(hasSuperType(named("org.reactivestreams.Subscription")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("cancel").and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("reactor");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.reactor.SubscriptionCancelInstrumentation$CancelAdvice";
    }

    public static class CancelAdvice {

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterCancel(@Advice.This Subscription subscription) {
            TracedSubscriber.onCancel(subscription);
        }
    }
}
