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
package co.elastic.apm.agent.log4j2.reformatting;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * The Log4j2 {@link Appender} does not expose a {@code setLayout()} API that allows us to override logging events.
 * However, it exposes an {@link Appender#getLayout()} API <b>that is always used when events are logged</b>.
 * Therefore, by instrumenting this method and replacing the returned {@link Layout}, we can implement the
 * {@link co.elastic.apm.agent.logging.LogEcsReformatting#OVERRIDE OVERRIDE} use case.
 */
public class Log4j2AppenderGetLayoutAdvice {

    private static final Log4J2EcsReformattingHelper helper = new Log4J2EcsReformattingHelper();

    @SuppressWarnings({"unused"})
    @Nullable
    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Layout<?> reformatLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) Appender thisAppender,
                                                 @Advice.Return @Nullable Layout<?> originalLayout) {

        if (originalLayout == null) {
            // Effectively disables instrumentation to all database appenders
            return null;
        }
        Layout<? extends Serializable> ecsLayout = helper.getEcsOverridingFormatterFor(thisAppender);
        if (ecsLayout != null) {
            return ecsLayout;
        }
        return originalLayout;
    }
}
