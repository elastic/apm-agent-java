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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.metadata.Destination;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

public class DubboTraceHelper {

    private static final String EXTERNAL_TYPE = "external";
    private static final String DUBBO_SUBTYPE = "dubbo";
    public static final String SPAN_KEY = "_elastic_apm_span";

    @Nullable
    public static Span<?> createConsumerSpan(Tracer tracer, Class<?> apiClass, String methodName, InetSocketAddress remoteAddress) {
        AbstractSpan<?> traceContext = tracer.getActive();
        if (traceContext == null) {
            return null;
        }
        Span<?> span = traceContext.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType(EXTERNAL_TYPE)
            .withSubtype(DUBBO_SUBTYPE);

        StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
        if (spanName != null) {
            String className = apiClass.getName();
            spanName.append(className, className.lastIndexOf('.') + 1, className.length());
            spanName.append("#").append(methodName);
        }

        Destination destination = span.getContext().getDestination();
        destination.withInetSocketAddress(remoteAddress);

        span.getContext().getServiceTarget()
            .withType(DUBBO_SUBTYPE)
            .withHostPortName(remoteAddress.getHostName(), remoteAddress.getPort())
            .withNameOnlyDestinationResource();

        return span.activate();
    }

    public static void fillTransaction(Transaction<?> transaction, Class<?> apiClass, String methodName) {
        StringBuilder spanName = transaction.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
        if (spanName != null) {
            String className = apiClass.getName();
            spanName.append(className, className.lastIndexOf('.') + 1, className.length());
            spanName.append("#").append(methodName);
        }
        transaction.withType(Transaction.TYPE_REQUEST);
    }
}
