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
package co.elastic.apm.agent.java_ldap;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import com.sun.jndi.ldap.Connection;
import com.sun.jndi.ldap.LdapResult;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import javax.annotation.Nullable;

public class LdapClientAdvice {

    private static final Tracer tracer = GlobalTracer.get();

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Origin("#m") String methodName, @Advice.FieldValue(value = "conn", typing = Assigner.Typing.DYNAMIC) Connection connection) {
        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }

        Span<?> span = parent.createExitSpan();
        if (span == null) {
            return null;
        }

        span.appendToName("LDAP ").appendToName(methodName)
            .withType("external")
            .withSubtype("ldap");

        if (connection != null) {
            span.getContext().getDestination().withAddress(connection.host).withPort(connection.port);
            span.getContext().getServiceTarget().withType("ldap").withHostPortName(connection.host, connection.port);
        }

        return span.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Object spanObj, @Nullable @Advice.Return LdapResult ldapResult, @Nullable @Advice.Thrown Throwable t) {
        Span<?> span = (Span<?>) spanObj;
        if (span != null) {
            span.withOutcome((ldapResult != null && ldapResult.status == 0 /* LDAP_SUCCESS */) ? Outcome.SUCCESS : Outcome.FAILURE)
                .captureException(t)
                .deactivate().end();
        }
    }
}
