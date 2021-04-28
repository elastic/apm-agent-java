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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;

import javax.annotation.Nullable;

public class Log4j2AppenderGetLayoutAdvice {

    @SuppressWarnings({"unused"})
    @Nullable
    @AssignTo.Return
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Layout<?> shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) Appender thisAppender,
                                              @Advice.Return @Nullable Layout<?> originalLayout) {

        Log4j2LogShadingHelper helper = Log4j2LogShadingHelper.instance();
        if (originalLayout == null) {
            // Effectively disables instrumentation to all database appenders
            return null;
        }
        Appender shadeAppender = helper.getShadeAppenderFor(thisAppender);
        if (shadeAppender != null && helper.isOverrideConfigured()) {
            return shadeAppender.getLayout();
        }
        return originalLayout;
    }
}
