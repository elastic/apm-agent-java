/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.TracerAwareElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class BaseJmsInstrumentation extends TracerAwareElasticApmInstrumentation {
    @SuppressWarnings("WeakerAccess")
    @Nullable
    @VisibleForAdvice
    // Referencing JMS classes is legal due to type erasure. The field must be public in order for it to be accessible from injected code
    public static HelperClassManager<JmsInstrumentationHelper<Destination, Message, MessageListener>> jmsInstrHelperManager;

    @SuppressWarnings("WeakerAccess")
    @Nullable
    @VisibleForAdvice
    public static MessagingConfiguration messagingConfiguration;

    private synchronized static void init(ElasticApmTracer tracer) {
        if (jmsInstrHelperManager == null) {
            jmsInstrHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                "co.elastic.apm.agent.jms.JmsInstrumentationHelperImpl",
                "co.elastic.apm.agent.jms.JmsInstrumentationHelperImpl$MessageListenerWrapper",
                "co.elastic.apm.agent.jms.JmsMessagePropertyAccessor");
        }

        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    BaseJmsInstrumentation(ElasticApmTracer tracer) {
        init(tracer);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jms");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("javax.jms.Message"));
    }
}
