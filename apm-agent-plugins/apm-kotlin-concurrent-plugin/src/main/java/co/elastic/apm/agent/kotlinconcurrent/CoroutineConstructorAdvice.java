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
package co.elastic.apm.agent.kotlinconcurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import kotlin.coroutines.AbstractCoroutineContextElement;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.ContinuationInterceptor;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.InvocationTargetException;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class CoroutineConstructorAdvice {
    public static final ByteBuddy byteBuddy = new ByteBuddy();
    public static final Object continuationTracingWrapperClassMonitor = new Object();
    public static final Object tracingContinuationInterceptorClassMonitor = new Object();

    private CoroutineConstructorAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) CoroutineContext coroutineContext)
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

        final AbstractSpan<?> context = TracerAwareInstrumentation.tracer.getActive();
        if (context != null) {
            context.incrementReferences();

            Class<?> continuationTracingWrapperClass;
            try {
                continuationTracingWrapperClass = Class.forName("ContinuationTracingWrapper");
            } catch (ClassNotFoundException e) {
                synchronized (continuationTracingWrapperClassMonitor) {
                    try {
                        continuationTracingWrapperClass = Class.forName("ContinuationTracingWrapper");
                    } catch (ClassNotFoundException classNotFoundException) {
                        continuationTracingWrapperClass = byteBuddy
                            .subclass(Continuation.class)
                            .modifiers(Visibility.PUBLIC)
                            .name("ContinuationTracingWrapper")
                            .defineField("span", AbstractSpan.class, Visibility.PRIVATE)
                            .defineField("continuation", Continuation.class, Visibility.PRIVATE)
                            .defineConstructor(Visibility.PUBLIC)
                            .withParameters(AbstractSpan.class, Continuation.class)
                            .intercept(
                                MethodCall.invoke(Object.class.getConstructor())
                                    .andThen(FieldAccessor.ofField("span").setsArgumentAt(0))
                                    .andThen(FieldAccessor.ofField("continuation").setsArgumentAt(1))
                            )
                            .method(named("getContext"))
                            .intercept(
                                MethodCall.invoke(Continuation.class.getDeclaredMethod("getContext"))
                                    .onField("continuation")
                            )
                            .method(named("resumeWith"))
                            .intercept(
                                MethodCall.invoke(AbstractSpan.class.getDeclaredMethod("activate"))
                                    .onField("span")
                                    .andThen(
                                        MethodCall.invoke(Continuation.class.getDeclaredMethod("resumeWith", Object.class))
                                            .onField("continuation")
                                            .withArgument(0)
                                    )
                                    .andThen(
                                        MethodCall.invoke(AbstractSpan.class.getDeclaredMethod("deactivate"))
                                            .onField("span")
                                    )
                            )
                            .make()
                            .load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded();
                    }
                }
            }

            Class<?> tracingContinuationInterceptor;
            try {
                tracingContinuationInterceptor = Class.forName("TracingContinuationInterceptor");
            } catch (ClassNotFoundException e) {
                synchronized (tracingContinuationInterceptorClassMonitor) {
                    try {
                        tracingContinuationInterceptor = Class.forName("TracingContinuationInterceptor");
                    } catch (ClassNotFoundException classNotFoundException) {
                        tracingContinuationInterceptor = byteBuddy
                            .subclass(AbstractCoroutineContextElement.class)
                            .name("TracingContinuationInterceptor")
                            .implement(ContinuationInterceptor.class)
                            .defineField("span", AbstractSpan.class, Visibility.PUBLIC)
                            .defineField("dispatcher", ContinuationInterceptor.class, Visibility.PRIVATE)
                            .defineConstructor(Visibility.PUBLIC)
                            .withParameters(AbstractSpan.class, ContinuationInterceptor.class)
                            .intercept(
                                MethodCall.invoke(AbstractCoroutineContextElement.class.getConstructor(CoroutineContext.Key.class))
                                    .withReference(ContinuationInterceptor.Key)
                                    .andThen(FieldAccessor.ofField("span").setsArgumentAt(0))
                                    .andThen(FieldAccessor.ofField("dispatcher").setsArgumentAt(1))
                            )


                            .method(named("interceptContinuation"))
                            .intercept(


                                MethodCall.invoke(ContinuationInterceptor.class.getDeclaredMethod("interceptContinuation", Continuation.class))
                                    .onField("dispatcher")
                                    .withMethodCall(
                                        MethodCall.construct(continuationTracingWrapperClass.getConstructor(AbstractSpan.class, Continuation.class))
                                            .withField("span")
                                            .withArgument(0)
                                    )
                                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)

                            )
                            .method(named("releaseInterceptedContinuation"))
                            .intercept(
                                MethodCall.invoke(ContinuationInterceptor.class.getDeclaredMethod("releaseInterceptedContinuation", Continuation.class))
                                    .onField("dispatcher")
                                    .withArgument(0)
                            )
                            .make()
                            .load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded();
                    }
                }
            }

            ContinuationInterceptor currentContinuationInterceptor = (ContinuationInterceptor) coroutineContext.get(
                (CoroutineContext.Key) ContinuationInterceptor.Key
            );
            CoroutineContext newContinuationInterceptor = (CoroutineContext) tracingContinuationInterceptor.getConstructor(
                AbstractSpan.class,
                ContinuationInterceptor.class
            ).newInstance(context, currentContinuationInterceptor);

            coroutineContext = coroutineContext.plus(newContinuationInterceptor);
        }
    }
}
