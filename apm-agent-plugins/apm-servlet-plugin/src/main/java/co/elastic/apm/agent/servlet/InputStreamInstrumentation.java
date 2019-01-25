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

import javax.annotation.Nullable;

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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.TransactionContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments the ServletInputStream read method.
 * <p>
 * Due to the fact that an InputStream can only be read once, the read method of the ServletInputStream has to be instrumented in order to passively read the
 * InputStream. Within the read method of the InputStream the content of the request body will be copied to the passed in byte array. Therefore the passed in
 * byte array will be copied when the read method exits. The copied data is stored in the transaction context and processed at a later stage.
 * </p>
 */
public class InputStreamInstrumentation extends ElasticApmInstrumentation {


    static final String SERVLET_API = "servlet-api";

    @Override
    public void init(ElasticApmTracer tracer) {
        InputStreamAdvice.tracer = tracer;
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("ServletInputStream");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // return any();
        return not(isInterface()).and(hasSuperType(named("javax.servlet.ServletInputStream")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("read").and(takesArgument(0, byte[].class)).and(takesArgument(1, int.class)).and(takesArgument(2, int.class));
    }

    @Override
    public Class<?> getAdviceClass() {
        return InputStreamAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    @VisibleForAdvice
    public static class InputStreamAdvice {
        @Nullable
        @VisibleForAdvice
        public static ElasticApmTracer tracer;

        @Nullable
        @VisibleForAdvice
        public static ThreadLocal<Boolean> reading = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onReadExit(@Advice.Argument(0) byte[] newData, @Advice.Argument(1) int from, @Advice.Argument(2) int to) {

            if (newData == null || tracer == null || tracer.currentTransaction() == null || tracer.currentTransaction().getContext() == null) {
                return;
            }

            TransactionContext context = tracer.currentTransaction().getContext();
            Map<String, Object> custom = context.getCustom();

            int numberOfBytesToRead = to - from;
            byte[] fullData = null;
            byte[] existingData = (byte[]) custom.get("REQUESTBODYDATA");

            if (existingData == null) {
                fullData = Arrays.copyOf(newData, numberOfBytesToRead);
            } else {
                try {
                    fullData = new byte[existingData.length + numberOfBytesToRead];
                    System.arraycopy(existingData, 0, fullData, 0, existingData.length);
                    System.arraycopy(newData, 0, fullData, existingData.length, numberOfBytesToRead);
                } catch (Exception e) {

                }
            }
            custom.put("REQUESTBODYDATA", fullData);
        }
    }
}
