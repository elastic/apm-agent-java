/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments {@link ManagementFactory#getPlatformMBeanServer()}
 * <p>
 * Makes sure that tracking of JMX metrics is only done after the {@linkplain ManagementFactory#getPlatformMBeanServer() platform MBean server}
 * has been created.
 * </p>
 * <p>
 * The problem is that by calling {@link ManagementFactory#getPlatformMBeanServer()} {@link java.util.logging.LogManager} gets initialized.
 * We have to ensure that containers such as WildFly have the chance to set a custom LogManager.
 * Otherwise, the startup fails with the following exception:
 * {@code java.lang.IllegalStateException: WFLYLOG0078:
 * The logging subsystem requires the log manager to be org.jboss.logmanager.LogManager.
 * The subsystem has not be initialized and cannot be used.
 * To use JBoss Log Manager you must add the system property "java.util.logging.manager" and set it to "org.jboss.logmanager.LogManager"}
 * </p>
 */
public class ManagementFactoryInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.lang.management.ManagementFactory");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getPlatformMBeanServer")
            .and(isStatic())
            .and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jmx");
    }

    @Advice.OnMethodExit
    private static void onExit(@Advice.Return MBeanServer mBeanServer) {
        if (tracer != null) {
            JmxMetricTracker jmxMetricTracker = tracer.getLifecycleListener(JmxMetricTracker.class);
            if (jmxMetricTracker != null) {
                jmxMetricTracker.onPlatformMBeanServerInitialized(mBeanServer);
            }
        }
    }
}
