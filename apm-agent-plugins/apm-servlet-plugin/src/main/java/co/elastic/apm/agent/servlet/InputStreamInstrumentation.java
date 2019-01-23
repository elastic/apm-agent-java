package co.elastic.apm.agent.servlet;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.context.TransactionContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link FilterChainInstrumentation}, it does not record the transaction again. But if there is
 * no filter registered for a servlet, this makes sure to record a transaction in that case.
 * </p>
 */
public class InputStreamInstrumentation extends ElasticApmInstrumentation {


    static final String SERVLET_API = "servlet-api";

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("ServletInputStream");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("java.io.InputStream")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("read").and(takesArgument(0, byte[].class)).and(takesArgument(1, int.class)).and(takesArgument(2, int.class));
    }

    @Override
    public Class<?> getAdviceClass() {
        return InputStreamInstrumentation.InputStreamAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    public static class InputStreamAdvice {
        @VisibleForAdvice
        public static ThreadLocal<Boolean> excluded = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onReadExit(@Advice.Argument(0) Object argument) {
            System.out.println("Hello");
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

}

