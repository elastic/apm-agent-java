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
package co.elastic.apm.agent.logback.correlation;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link ch.qos.logback.classic.Logger#callAppenders(ILoggingEvent)}
 */
public class LogbackLogCorrelationInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_CORRELATION;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("ch.qos.logback.classic.Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("callAppenders");
    }

    public static class AdviceClass {

        private static final LogbackLogCorrelationHelper helper = new LogbackLogCorrelationHelper();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static boolean addToMdc() {
            return helper.beforeLoggingEvent();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void removeFromMdc(@Advice.Enter boolean addedToMdc) {
            helper.afterLoggingEvent(addedToMdc);
        }
    }
}
