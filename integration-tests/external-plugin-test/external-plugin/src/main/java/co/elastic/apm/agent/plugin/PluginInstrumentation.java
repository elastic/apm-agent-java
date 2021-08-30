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
package co.elastic.apm.agent.plugin;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class PluginInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named(System.getProperty("elastic.apm.plugin.instrumented_class", "co.elastic.apm.plugin.test.TestClass"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named(System.getProperty("elastic.apm.plugin.instrumented_method", "traceMe"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("test-plugin");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$AdviceClass";
    }

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Origin(value = "#m") String methodName) {
            Span ret;
            Transaction transaction = ElasticApm.currentTransaction();
            if (transaction.getId().isEmpty()) {
                // the NoopTransaction
                ret = ElasticApm.startTransaction();
                System.out.println("ret = " + ret);
            } else {
                ret = transaction.startSpan("plugin", "external", "trace");
                System.out.println("ret = " + ret);
            }
            return ret.setName(methodName).activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown, @Advice.Enter Object scopeObject) {
            try {
                Span span = ElasticApm.currentSpan();
                System.out.println("span = " + span);
                span.captureException(thrown);
                span.end();
            } finally {
                ((Scope) scopeObject).close();
            }
        }
    }
}
