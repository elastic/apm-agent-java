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
package co.elastic.apm.agent.webflux.disabled;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.http.server.reactive.ServletHttpHandlerAdapter#service(ServletRequest, ServletResponse)}
 * to create transactions for annotated controllers.
 */
public class ServletHttpHandlerAdapterInstrumentation extends ElasticApmInstrumentation {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ServletHttpHandlerAdapterInstrumentation.class);

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void beforeService(@Advice.Argument(value = 0) ServletRequest request,
                                     @Advice.Local("transaction") @Nullable Transaction transaction) {
        if (tracer == null) {
            logger.trace("beforeService tracer == null");
            return;
        }
        if (tracer.getActive() != null) {
            logger.trace("beforeService tracer.getActive() != null");
            return;
        }
        if (transaction == null) {
            transaction = WebFluxInstrumentationHelper.createAndActivateTransaction(tracer, request);
        }
    }

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void afterService(@Advice.Argument(value = 0) ServletRequest request,
                                    @Advice.Argument(value = 1) ServletResponse response,
                                    @Advice.Local("transaction") @Nullable Transaction transaction,
                                    @Advice.Thrown @Nullable Throwable t) {
        if (transaction != null) {
            System.out.println("afterService transaction != null" + transaction);
            transaction.captureException(t)
                .deactivate()
                .end();
        } else {
            logger.trace("afterService transaction == null");
        }
    }


    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.http.server.reactive.ServletHttpHandlerAdapter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-reactive-servlet-handler");
    }
}
