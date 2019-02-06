package co.elastic.apm.agent.servlet;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.util.Collection;
import java.util.Collections;

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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.IOUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments the ServletInputStream read and readLine method.
 * <p>
 * Due to the fact that an InputStream can only be read once, the read and readLine method of the ServletInputStream has to be instrumented in order to
 * passively read the InputStream. Within the afore mentioned methods the content of the request body will be copied to the byte array that is passed in to
 * these methods as an argument. Therefore the passed in byte array will be copied when the read method exits. The copied data is stored in the transaction
 * context and processed at a later stage.
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
        return nameContains("Stream");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.servlet.ServletInputStream")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return (named("read").or(named("readLine"))).and(takesArgument(0, byte[].class)).and(takesArgument(1, int.class)).and(takesArgument(2, int.class));
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

        @VisibleForAdvice
        public static ThreadLocal<Boolean> reading = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onReadEnter(@Advice.This Object thiz, @Advice.Local("transaction") Transaction transaction,
                @Advice.Local("alreadyReading") boolean alreadyReading) {
            alreadyReading = reading.get();
            reading.set(Boolean.TRUE);
        }


        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onReadExit(@Advice.Argument(0) byte[] bytes, @Advice.Argument(1) int offset, @Advice.Argument(2) int length,
                @Nullable @Advice.Thrown Throwable t, @Advice.Local("alreadyReading") boolean alreadyReading) {
            try {
                if (alreadyReading || 
                    bytes == null || 
                    tracer == null || 
                    tracer.currentTransaction() == null ||
                    tracer.currentTransaction().getContext() == null ||
                    tracer.currentTransaction().getContext().getRequest() == null || 
                    t != null ) {
                    return;
                }
                
                IOUtils.decodeUtf8Bytes(bytes, offset, length, tracer.currentTransaction().getContext().getRequest().withBodyBuffer());
            } finally {
                reading.set(Boolean.FALSE);
            }
        }
    }
}
