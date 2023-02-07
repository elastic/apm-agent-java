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
package co.elastic.apm.agent.jul.reformatting;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class JulConsoleHandlerPublishAdvice {

    private static final JulEcsReformattingHelper<ConsoleHandler> helper = new JulEcsReformattingHelper<ConsoleHandler>();

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class, inline = false)
    public static boolean initializeReformatting(@Advice.This(typing = Assigner.Typing.DYNAMIC) ConsoleHandler thisHandler) {
        return helper.onAppendEnter(thisHandler);
    }

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void reformatLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogRecord logRecord,
                                            @Advice.This(typing = Assigner.Typing.DYNAMIC) ConsoleHandler thisHandler) {


        helper.onAppendExit(logRecord, thisHandler);
    }
}
