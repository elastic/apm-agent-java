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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.premain.ThreadUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Prevents the agent from initializing JVM singletons that read SSL configuration before the application has the chance to configure
 * system properties that influence the initialization.
 * <p>
 * If any of these methods is called within the context of an {@code elastic-apm} thread,
 * the method is skipped.
 * </p>
 * <ul>
 *   <li>{@link SSLContext#getDefault()}</li>
 *   <li>{@link SocketFactory#getDefault()}</li>
 *   <li>{@link SSLSocketFactory#getDefault()}</li>
 * </ul>
 */
public class SSLContextInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("javax.net.ssl.SSLContext")
            .or(named("javax.net.SocketFactory"))
            .or(named("javax.net.ssl.SSLSocketFactory"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getDefault")
            .and(isPublic())
            .and(isStatic())
            .and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("ssl-context");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$AdviceClass";
    }

    public static class AdviceClass {
        /**
         * This will not allow using the default SSL factory from any agent thread
         */
        @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class, inline = false)
        public static boolean skipExecutionIfAgentThread() {
            return Thread.currentThread().getName().startsWith(ThreadUtils.ELASTIC_APM_THREAD_PREFIX);
        }
    }

}
