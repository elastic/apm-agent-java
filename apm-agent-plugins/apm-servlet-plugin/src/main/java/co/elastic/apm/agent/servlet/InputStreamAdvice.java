/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.servlet;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nullable;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;

public class InputStreamAdvice {
    @Nullable
    @VisibleForAdvice
    public static ServletTransactionHelper servletTransactionHelper;
    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;
    @VisibleForAdvice
    public static ThreadLocal<Boolean> excluded = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    static void init(ElasticApmTracer tracer) {
        InputStreamAdvice.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onReadEnter(@Advice.This Object thiz, @Advice.Origin Method advicedObject, @Advice.Local("transaction") Transaction transaction,
            @Advice.Local("scope") Scope scope) {
        // TODO Remove method from instrumentation
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onReadExit(@Advice.Argument(0) Object argument) {

        if (argument == null || tracer == null || tracer.currentTransaction() == null || tracer.currentTransaction().getContext() == null) {
            return;
        }

        System.out.println(new String((byte[]) argument));

        TransactionContext context = tracer.currentTransaction().getContext();
        Map<String, Object> custom = context.getCustom();

        byte[] fullData = null;
        byte[] existingData = (byte[]) custom.get("REQUESTBODYDATA");
        byte[] newData = (byte[]) argument;

        if (existingData == null) {
            fullData = Arrays.copyOf(newData, newData.length);
        } else {
            fullData = new byte[existingData.length + newData.length];
            System.arraycopy(existingData, 0, fullData, 0, existingData.length);
            System.arraycopy(newData, 0, fullData, existingData.length, newData.length);
        }
        custom.put("REQUESTBODYDATA", fullData);
    }
}
