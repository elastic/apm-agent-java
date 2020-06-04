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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.util.ThreadUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * //todo
 */
public class HttpsUrlConnectionInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("httpsurlconnection");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("javax.net.ssl.HttpsURLConnection");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isStatic().and(named("getDefaultSSLSocketFactory")).and(returns(named("javax.net.ssl.SSLSocketFactory")));
    }

    /**
     * This will not allow using the default SSL factory from any agent thread
     */
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static boolean skipExecutionIfAgentThread() {
        return Thread.currentThread().getName().startsWith(ThreadUtils.ELASTIC_APM_THREAD_PREFIX);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopInitializationDelay(@Nullable @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object sslFactory) {
        System.out.println("sslFactory = " + sslFactory);
        if (tracer != null && sslFactory != null) {
            tracer.stopInitializationDelay();
        }
    }
}
