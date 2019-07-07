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
package co.elastic.apm.agent.opentracing.impl;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class LoggerInstrumentation extends OpenTracingBridgeInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(LoggerInstrumentation.class);

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public LoggerInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class LogWarningInstrumentation extends LoggerInstrumentation {

        public LogWarningInstrumentation() {
            super(named("warn"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void logWarning(@Advice.Argument(0) String message,
                                      @Advice.Argument(1) @Nullable Throwable throwable) {
            if (throwable != null) {
                logger.warn(message, throwable);
            } else {
                logger.warn(message);
            }
        }
    }

    public static class LogDebugInstrumentation extends LoggerInstrumentation {

        public LogDebugInstrumentation() {
            super(named("debug"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void logDebug(@Advice.Argument(0) String message,
                                    @Advice.Argument(1) @Nullable Throwable throwable) {
            if (throwable != null) {
                logger.debug(message, throwable);
            } else {
                logger.debug(message);
            }
        }
    }
}
