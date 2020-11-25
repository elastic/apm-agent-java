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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class BaseJmsInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jms");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("javax.jms.Message"));
    }

    protected static class BaseAdvice {

        protected static final JmsInstrumentationHelper helper;
        protected static final MessagingConfiguration messagingConfiguration;
        protected static final CoreConfiguration coreConfiguration;

        static {
            ElasticApmTracer elasticApmTracer = GlobalTracer.requireTracerImpl();

            // loading helper class will load JMS-related classes if loaded from Instrumentation static init
            // that fails when trying to load instrumentation classes without JMS dependencies, for example when generating
            // documentation that relies on instrumentation group names
            helper = new JmsInstrumentationHelper(elasticApmTracer);

            messagingConfiguration = elasticApmTracer.getConfig(MessagingConfiguration.class);
            coreConfiguration = elasticApmTracer.getConfig(CoreConfiguration.class);
        }
    }
}
