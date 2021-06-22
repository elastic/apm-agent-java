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
package co.elastic.apm.agent.log4j2;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;

public class Log4j2AppenderAppendAdvice {

    private static final Log4J2EcsReformattingHelper helper = new Log4J2EcsReformattingHelper();

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class, inline = false)
    public static boolean shadeAndSkipIfReplaceEnabled(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogEvent eventObject,
                                                       @Advice.This(typing = Assigner.Typing.DYNAMIC) Appender thisAppender) {
        return helper.onAppendEnter(thisAppender);
    }

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogEvent eventObject,
                                         @Advice.This(typing = Assigner.Typing.DYNAMIC) Appender thisAppender) {

        Appender shadeAppender = helper.onAppendExit(thisAppender);
        if (shadeAppender != null) {
            shadeAppender.append(eventObject);
        }
    }
}
