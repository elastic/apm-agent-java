/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx_3_6;

import co.elastic.apm.agent.concurrent.JavaConcurrent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class VertxTaskQueueInstrumentation extends VertxWebInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.core.impl.TaskQueue");
    }

    /**
     * Instruments {@link io.vertx.core.impl.TaskQueue#execute(Runnable, Executor)} to avoid context propagation
     * through the java concurrent plugin for the task queue.
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(takesArguments(Runnable.class, Executor.class));
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter() {
        JavaConcurrent.avoidPropagationOnCurrentThread();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Thrown @Nullable Throwable thrown) {
        JavaConcurrent.allowContextPropagationOnCurrentThread();
    }
}
