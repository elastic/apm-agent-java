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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class PluginInstrumentation extends ElasticApmInstrumentation {

    /**
     * This slf4j logger relies on the slf4j that comes from the instrumented library (see {@link co.elastic.apm.plugin.test.TestClass}).
     * If an external plugin contains slf4j classes, it will be rejected by the agent.
     * Same for all other packages listed in {@code co.elastic.apm.agent.common.util.AgentInfo#dependencyPackages}.
     * All these packages can be used only if their scope is {@code provided}.
     */
    private static final Logger logger = LoggerFactory.getLogger(PluginInstrumentation.class);

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

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Origin Class<?> clazz, @Advice.Origin(value = "#m") String methodName) {
            Span ret;
            Transaction transaction = ElasticApm.currentTransaction();
            if (transaction.getId().isEmpty()) {
                // the NoopTransaction
                ret = ElasticApm.startTransaction();
                ret.setName(clazz.getSimpleName() + "#" + methodName);
            } else {
                ret = transaction.startSpan("plugin", "external", "trace");
                ret.setName(methodName);
            }
            logger.info("ret = " + ret);
            return ret.activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown, @Advice.Enter Object scopeObject) {
            try {
                Span span = ElasticApm.currentSpan();
                logger.info("span = " + span);
                span.captureException(thrown);
                span.end();
            } finally {
                ((Scope) scopeObject).close();
            }
        }
    }
}
