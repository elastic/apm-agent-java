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
package co.elastic.apm.agent.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class LogbackAppenderAppendAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class, inline = false)
    public static boolean shadeAndSkipIfOverrideEnabled(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object eventObject,
                                                        @Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
        return thisAppender instanceof FileAppender && eventObject instanceof ILoggingEvent &&
            LogbackLogShadingHelper.instance().onAppendEnter((FileAppender<ILoggingEvent>) thisAppender);
    }

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object eventObject,
                                         @Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
        if (!LogbackLogShadingHelper.instance().onAppendExit() || !(thisAppender instanceof FileAppender) || !(eventObject instanceof ILoggingEvent)) {
            return;
        }
        FileAppender<ILoggingEvent> shadeAppender = LogbackLogShadingHelper.instance().getShadeAppenderFor((FileAppender<ILoggingEvent>) thisAppender);

        if (shadeAppender != null) {
            // We do not invoke the exact same method we instrument, but a public API that calls it
            shadeAppender.doAppend((ILoggingEvent) eventObject);
        }
    }
}
