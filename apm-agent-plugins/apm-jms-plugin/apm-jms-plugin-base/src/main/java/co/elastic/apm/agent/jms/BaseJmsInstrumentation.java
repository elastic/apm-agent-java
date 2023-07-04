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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.MessagingConfiguration;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class BaseJmsInstrumentation extends TracerAwareInstrumentation {

    protected final MessagingConfiguration messagingConfiguration;

    protected BaseJmsInstrumentation() {
        Tracer tracer = GlobalTracer.get();
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jms");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass(rootClassNameThatClassloaderCanLoad()));
    }

    public ElementMatcher<? super NamedElement> getConsumerPreFilterTypeMatcher() {
        return nameContains("Message")
            .or(nameContains("Consumer"))
            .or(nameContains("Receiver"))
            .or(nameContains("Subscriber"));
    }

    public ElementMatcher<? super NamedElement> getProducerPreFilterTypeMatcher() {
        return nameContains("Message")
            .or(nameContains("Producer"))
            .or(nameContains("Sender"))
            .or(nameContains("Publisher"));
    }

    public ElementMatcher<? super MethodDescription> getReceiverMethodMatcher() {
        return named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic())
            .or(named("receiveNoWait").and(takesArguments(0).and(isPublic())));
    }

    public ElementMatcher<? super NamedElement> getMessageListenerTypeMatcherPreFilter() {
        ElementMatcher.Junction<NamedElement> nameHeuristic =
            nameContains("$") // inner classes
                .or(nameContains("Message"))
                .or(nameContains("Listener"));

        Collection<String> listenerPackages = messagingConfiguration.getJmsListenerPackages();
        if (listenerPackages.isEmpty()) {
            // default heuristic
            return nameHeuristic;
        } else {
            // expand the default heuristic with the provided listener package list
            return nameHeuristic.or(isInAnyPackage(listenerPackages, ElementMatchers.<NamedElement>none()));
        }
    }

    public abstract String rootClassNameThatClassloaderCanLoad();

    protected static class BaseAdvice {

        protected static final CoreConfiguration coreConfiguration;

        static {
            Tracer tracer = GlobalTracer.get();
            coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        }
    }
}
