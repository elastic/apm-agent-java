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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_DEFAULT;

@VisibleForAdvice
public class DubboTraceHelper {

    private static final String EXTERNAL_TYPE = "external";
    private static final String DUBBO_SUBTYPE = "dubbo";
    @VisibleForAdvice
    public static final String SPAN_KEY = "_elastic_apm_span";

    @Nullable
    @VisibleForAdvice
    public static Span createConsumerSpan(ElasticApmTracer tracer, Class<?> apiClass, String methodName, InetSocketAddress remoteAddress) {
        AbstractSpan<?> traceContext = tracer.getActive();
        if (traceContext == null) {
            return null;
        }
        Span span = traceContext.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType(EXTERNAL_TYPE)
            .withSubtype(DUBBO_SUBTYPE);
        fillName(span, apiClass, methodName);

        Destination destination = span.getContext().getDestination();
        destination.withInetSocketAddress(remoteAddress);

        Destination.Service service = destination.getService();
        service.withType(EXTERNAL_TYPE).withName(DUBBO_SUBTYPE);
        service.getResource().append(remoteAddress.getHostName()).append(':').append(remoteAddress.getPort());

        return span.activate();
    }

    private static void fillName(AbstractSpan<?> span, Class<?> apiClass, String methodName) {
        StringBuilder spanName = span.getAndOverrideName(PRIO_DEFAULT);
        if (spanName != null) {
            appendSimpleClassName(apiClass, spanName);
            spanName.append("#")
                .append(methodName);
        }
    }

    public static void appendSimpleClassName(Class<?> clazz, StringBuilder sb) {
        String className = clazz.getName();
        sb.append(className, className.lastIndexOf('.') + 1, className.length());
    }

    @VisibleForAdvice
    public static void fillTransaction(Transaction transaction, Class<?> apiClass, String methodName) {
        fillName(transaction, apiClass, methodName);
        transaction.withType(Transaction.TYPE_REQUEST);
    }
}
