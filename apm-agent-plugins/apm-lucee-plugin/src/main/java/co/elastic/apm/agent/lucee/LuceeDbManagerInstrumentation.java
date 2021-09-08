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
package co.elastic.apm.agent.lucee;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Collection;
import java.util.Arrays;

public class LuceeDbManagerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("lucee.runtime.db.DataSourceManager"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return takesArguments(0).and(
                named("begin").
                or(named("rollback")).
                or(named("savepoint")).
                or(named("commit")).
                or(named("end"))
        );
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("lucee", "transaction");
    }

    @Override
    public String getAdviceClassName() {
        return CfDBMBeginStringAdvice.class.getName();
    }
    public static class CfDBMBeginStringAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Origin("#m") String methodName) {

            if (tracer == null || tracer.getActive() == null || methodName == null) {
                return null;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            Object span = parent.createSpan()
                    .withName(methodName)
                    .withType("db")
                    .withSubtype("transaction");

            if (span != null) {
                ((Span)span).activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    ((Span)span).captureException(t);
                } finally {
                    ((Span)span).deactivate().end();
                }
            }
        }
    }
}
