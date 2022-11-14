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

import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class JulFileHandlerPublishAdvice {

    private static final JulEcsReformattingHelper helper = new JulEcsReformattingHelper();

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class, inline = false)
    public static boolean initializeReformatting(@Advice.This(typing = Assigner.Typing.DYNAMIC) FileHandler thisHandler,
                                                 @Advice.FieldValue("pattern") String pattern,
                                                 @Advice.FieldValue("files") File[] files) {
        return helper.onAppendEnter(thisHandler, pattern, files[0]);
    }

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void reformatLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogRecord logRecord,
                                            @Advice.This(typing = Assigner.Typing.DYNAMIC) FileHandler thisHandler) {

        Handler shadeAppender = helper.onAppendExit(thisHandler);
        if (shadeAppender != null) {
            shadeAppender.publish(logRecord);
        }
    }
}
