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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.net.InetSocketAddress;

@VisibleForAdvice
public class DubboTraceHelper {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    private static final String EXTERNAL_TYPE = "external";

    private static final String DUBBO_SUBTYPE = "dubbo";

    public static final String SPAN_KEY = "span";

    public static void init(ElasticApmTracer tracer) {
        DubboTraceHelper.tracer = tracer;
    }

    @VisibleForAdvice
    public static Span createConsumerSpan(Class<?> apiClass, String methodName, Class<?>[] paramClasses,
                                          String version, InetSocketAddress remoteAddress) {
        TraceContextHolder<?> traceContext = DubboTraceHelper.tracer.getActive();
        if (traceContext == null) {
            return null;
        }
        Span span = traceContext.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType(EXTERNAL_TYPE)
            .withSubtype(DUBBO_SUBTYPE);
        fillName(span, apiClass, methodName, paramClasses, version);

        Destination destination = span.getContext().getDestination();
        destination.withAddress(remoteAddress.getHostName()).withPort(remoteAddress.getPort());

        Destination.Service service = destination.getService();
        service.withType(EXTERNAL_TYPE).withResource(DUBBO_SUBTYPE).withName(DUBBO_SUBTYPE);

        return span.activate();
    }

    private static void fillName(AbstractSpan<?> span, Class<?> apiClass, String methodName,
                                 Class<?>[] paramClasses, String version) {
        span.appendToName(apiClass.getName())
            .appendToName(".")
            .appendToName(methodName)
            .appendToName("(");
        if (paramClasses != null) {
            int length = paramClasses.length;
            for (int i = 0; i < paramClasses.length; i++) {
                span.appendToName(paramClasses[i].getSimpleName());
                if (i < length - 1) {
                    span.appendToName(",");
                }
            }
        }
        span.appendToName(")");
        if (version != null && version.length() > 0) {
            span.appendToName("version=").appendToName(version);
        }
    }

    @VisibleForAdvice
    public static void fillTransaction(Transaction transaction, Class<?> apiClass, String methodName,
                                       Class<?>[] paramClasses, String version) {
        fillName(transaction, apiClass, methodName, paramClasses, version);
        transaction.withType("dubbo");
        transaction.activate();
    }

    public static void doCapture(Transaction transaction, Object[] args, Throwable t, Object returnValue) {
        if (transaction == null) {
            return;
        }
        boolean hasError = t != null;
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
        CoreConfiguration.EventType captureBody = coreConfig.getCaptureBody();
        if (CoreConfiguration.EventType.OFF.equals(captureBody) ||
            (CoreConfiguration.EventType.ERRORS.equals(captureBody) && !hasError)) {
            return;
        }

        captureArgs(transaction, args);
        if (t != null) {
            transaction.addCustomContext("throw", t.toString());
        } else {
            transaction.addCustomContext("return", returnValue.toString());
        }
    }

    public static void captureArgs(Transaction transaction, Object[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                transaction.addCustomContext("arg-" + i, args[i].toString());
            }
        }
    }
}
