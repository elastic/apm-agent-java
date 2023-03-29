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
package co.elastic.apm.agent.jbosslogging.correlation;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.logmanager.ExtLogRecord;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.jboss.logmanager.Logger#logRaw}
 */
public class JBossLogManagerCorrelationInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_CORRELATION;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.jboss.logmanager.Logger"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.jboss.logmanager.Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("logRaw").and(takesArgument(0, named("org.jboss.logmanager.ExtLogRecord")));
    }

    public static class AdviceClass {
        private static final JBossLogManagerCorrelationHelper helper = new JBossLogManagerCorrelationHelper();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static boolean addToMdc(@Advice.Argument(0) ExtLogRecord record) {
            return helper.beforeLoggingEvent(record);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void removeFromMdc(@Advice.Enter boolean addedToMdc) {
            helper.afterLoggingEvent(addedToMdc);
        }
    }
}
