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
package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractJsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("render")
            .and(takesArguments(1))
            .and(takesArgument(0, named(facesContextClassName())));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        List<String> ret = new ArrayList<>(super.getInstrumentationGroupNames());
        ret.add("render");
        return ret;
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.jsf.AbstractJsfLifecycleRenderInstrumentation$AdviceClass";
    }

    abstract String facesContextClassName();

    public static class AdviceClass {
        private static final String SPAN_ACTION = "render";

        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object createRenderSpan() {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            if (parent instanceof Span<?>) {
                Span<?> parentSpan = (Span<?>) parent;
                if (SPAN_SUBTYPE.equals(parentSpan.getSubtype()) && SPAN_ACTION.equals(parentSpan.getAction())) {
                    return null;
                }
            }
            Span<?> span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(SPAN_SUBTYPE)
                .withAction(SPAN_ACTION)
                .withName("JSF Render");
            span.activate();
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void endRenderSpan(@Advice.Enter @Nullable Object span,
                                         @Advice.Thrown @Nullable Throwable t) {

            if (span instanceof Span<?>) {
                ((Span<?>) span).captureException(t).deactivate().end();
            }
        }
    }
}
