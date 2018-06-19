/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.opentracing.impl;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.OPENTRACING_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class ApmScopeInstrumentation extends ElasticApmInstrumentation {


    @VisibleForAdvice
    @Advice.OnMethodEnter(inline = false)
    public static void release(@Nullable Object transaction, @Nullable Object span) {
        if (tracer != null) {
            if (transaction != null) {
                tracer.releaseActiveTransaction();
            } else if (span != null) {
                tracer.releaseActiveSpan();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ApmScope");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("release");
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public String getInstrumentationGroupName() {
        return OPENTRACING_INSTRUMENTATION_GROUP;
    }
}
