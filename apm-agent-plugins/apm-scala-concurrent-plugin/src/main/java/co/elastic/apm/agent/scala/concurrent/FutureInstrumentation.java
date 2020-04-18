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
package co.elastic.apm.agent.scala.concurrent;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class FutureInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("scala.concurrent.Future"))
            .or(hasSuperType(named("scala.concurrent.impl.Promise")))
            .or(hasSuperType(named("scala.concurrent.impl.Promise$Transformation")))
            .or(hasSuperType(named("scala.concurrent.Future$")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onComplete").and(returns(void.class))
            .or(named("transform").and(returns(named("scala.concurrent.Future"))))
            .or(named("transformWith").and(returns(named("scala.concurrent.Future"))))
            .or(named("result"));
    }

    @Nonnull
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "future");
    }

    @VisibleForAdvice
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
        System.out.println("DEBUG2");
        final TraceContextHolder<?> active = getActive();
        System.out.println(tracer.isWrappingAllowedOnThread());
        if (active != null && tracer != null && tracer.isWrappingAllowedOnThread()) {
            // Do no discard branches leading to async operations so not to break span references
            active.setDiscard(false);
            tracer.avoidWrappingOnThread();
        }
    }

}
