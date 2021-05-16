/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.agent.wildfly_ejb;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import org.jboss.ejb.client.EJBLocator;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteEJBClientAdvice {

    private static final String EXTERNAL_TYPE = "external";

    private static final String EJB_SUBTYPE = "ejb";

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterInvoke(@Advice.FieldValue("locatorRef") AtomicReference<EJBLocator<?>> locatorRef, @Advice.Argument(1) Method method) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }

        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }

        Span span = parent.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType(EXTERNAL_TYPE)
            .withSubtype(EJB_SUBTYPE)
            .appendToName(locatorRef.get().getViewType().getSimpleName()).appendToName("#").appendToName(method.getName());

        return span.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitInvoke(@Advice.Enter @Nullable Object spanOrNull, @Advice.Thrown @Nullable Throwable t) {
        if (spanOrNull == null) {
            return;
        }

        Span span = (Span) spanOrNull;
        span.captureException(t)
            .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
            .deactivate()
            .end();
    }
}
