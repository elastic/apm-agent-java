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
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import org.jboss.as.ee.component.ComponentView;
import org.jboss.ejb.server.InvocationRequest;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public class RemoteEJBServerAdvice {

    private static final String FRAMEWORK_NAME = "EJB";

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterInvokeMethod(@Advice.Argument(0) ComponentView componentView, @Advice.Argument(1) Method method, @Advice.Argument(3) InvocationRequest.Resolved invocationRequest) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }

        Transaction transaction = tracer.startChildTransaction(invocationRequest.getAttachments(), MapTextHeaderAccessor.INSTANCE, Thread.currentThread().getContextClassLoader());
        if (transaction == null) {
            return null;
        }

        transaction.withType(Transaction.TYPE_REQUEST)
            .appendToName(componentView.getViewClass().getSimpleName()).appendToName("#").appendToName(method.getName());
        transaction.setFrameworkName(FRAMEWORK_NAME);

        return transaction.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitInvokeMethod(@Advice.Enter @Nullable Object transactionOrNull, @Advice.Thrown @Nullable Throwable t) {
        if (transactionOrNull == null) {
            return;
        }

        Transaction transaction = ((Transaction) transactionOrNull);
        transaction.captureException(t)
            .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
            .deactivate()
            .end();
    }
}
