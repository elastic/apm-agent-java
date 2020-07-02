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
package co.elastic.apm.agent.jul;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.util.GlobalLocks;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class LogManagerInstrumentation extends ElasticApmInstrumentation {

    /**
     * A state making sure we lock when JUL is used only at startup. No need to be volatile, better lock few too many
     * times then making volatile accesses for the entire JVM lifetime
     */
    @VisibleForAdvice
    public static boolean initialized = false;

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.util.logging.LogManager");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("ensureLogManagerInitialized");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jul");
    }

    @Override
    public Class<?> getAdviceClass() {
        return LogManagerAdvice.class;
    }

    public static class LogManagerAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static boolean lock() {
            boolean locked = false;
            if (!initialized) {
                GlobalLocks.JUL_INIT_LOCK.lock();
                locked = true;
            }
            return locked;
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void unlock(@Advice.Enter boolean locked) {
            if (locked) {
                initialized = true;
                GlobalLocks.JUL_INIT_LOCK.unlock();
            }
        }
    }
}
