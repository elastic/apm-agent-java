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
package co.elastic.apm.agent.wildfly_ejb;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import org.jboss.ejb.client.EJBClientInvocationContext;

import java.net.URI;

public class RemoteEJBClientDestinationAdvice {

    private static final String EJB_TYPE = "ejb";

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitSendRequestInitial(@Advice.This EJBClientInvocationContext ejbClientInvocationContext) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return;
        }

        Span span = tracer.getActiveSpan();
        if (span == null) {
            return;
        }

        URI destination = ejbClientInvocationContext.getDestination();

        span.getContext().getDestination()
            .withAddress(destination.getHost())
            .getService()
            .withType(EJB_TYPE)
            .withResource(destination.getHost())
            .withName(destination.toString());

        if (destination.getPort() > 0) {
            span.getContext().getDestination()
                .withPort(destination.getPort())
                .getService()
                .getResource().append(":").append(destination.getPort());
        }
    }
}
