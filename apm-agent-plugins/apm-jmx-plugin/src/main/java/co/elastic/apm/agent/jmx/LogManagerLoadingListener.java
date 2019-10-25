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

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments the static initializer of {@link java.util.logging.LogManager}
 * <p>
 * Makes sure that tracking of JMX metrics is only done after {@link java.util.logging.LogManager} has been loaded an initialized.
 * </p>
 * <p>
 * The problem is that by calling {@link ManagementFactory#getPlatformMBeanServer()} {@link java.util.logging.LogManager} gets initialized.
 * We have to ensure that containers such as WildFly have the chance to set a custom LogManager to avoid this pitfall:
 * {@code java.lang.IllegalStateException: WFLYLOG0078:
 * The logging subsystem requires the log manager to be org.jboss.logmanager.LogManager.
 * The subsystem has not be initialized and cannot be used.
 * To use JBoss Log Manager you must add the system property "java.util.logging.manager" and set it to "org.jboss.logmanager.LogManager"}
 * </p>
 */
public class LogManagerLoadingListener extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.util.logging.LogManager");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isTypeInitializer();
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jmx");
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public void onTypeMatch(TypeDescription typeDescription, ClassLoader classLoader, ProtectionDomain protectionDomain, @Nullable Class<?> classBeingRedefined) {
        if (classBeingRedefined != null) {
            // class is already loaded so the static initializer has already been executed
            startMonitoringJmxMetrics();
        }
        // if the class is not loaded yet, we can't initialize the platform MBean server as this would lead to a ClassCircularityError
        // because we'd indirectly request loading the class java.util.logging.LogManager while loading the class
        // that's why we instrument the static initializer and start tracking JMX metrics after the class has been initialized
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void startMonitoringJmxMetrics() {
        if (tracer != null) {
            JmxMetricTracker jmxMetricTracker = tracer.getLifecycleListener(JmxMetricTracker.class);
            if (jmxMetricTracker != null) {
                jmxMetricTracker.onLogManagerInitialized(ManagementFactory.getPlatformMBeanServer());
            }
        }
    }
}
