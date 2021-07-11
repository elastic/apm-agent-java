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
package co.elastic.apm.agent.scalaconcurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.cache.WeakKeySoftValueLoadingCache;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.util.Try;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;
import co.elastic.apm.agent.concurrent.JavaConcurrent;

import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class FutureInstrumentation extends TracerAwareInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final WeakConcurrentMap<Object, AbstractSpan<?>> promisesToContext =
        new WeakConcurrentMap.WithInlinedExpunction<>();

    private static final Logger logger = LoggerFactory.getLogger(FutureInstrumentation.class);

    @Nonnull
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("scala-future", "experimental");
    }

    public static class BatchedExecutionContextInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("scala.concurrent.BatchingExecutor"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("submitForExecution").and(returns(void.class)).and(takesArguments(Runnable.class));
        }

        @Nullable
        @AssignTo.Argument(0)
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Runnable onExecute(@Advice.Argument(0) @Nullable Runnable runnable) {
            return JavaConcurrent.withContext(runnable, tracer);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                  @Advice.Argument(value = 0) @Nullable Runnable runnable) {
            JavaConcurrent.doFinally(thrown, runnable);
        }
    }

//    public static class FutureObjectInstrumentation extends FutureInstrumentation {
//
//        @Override
//        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
//            return named("scala.concurrent.Future$");
//        }
//
//        @Override
//        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
//            return isTypeInitializer();
//        }
//
//        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
//        public static void onExit() {
//            logger.warn("==============");
//            logger.warn("Match on Future.unit");
//            logger.warn("==============");
//
//            final AbstractSpan<?> context = tracer.getActive();
//            if (context != null) {
//                logger.warn("==============");
//                logger.warn("Match on Future.unit " + context);
//                logger.warn("==============");
//            }
//            // Remove context on the initial Future.unit initialization such that following
//            // chaining methods are not linked to this constant 'origin' Future.
//            final Try<?> unitFuture = Future$.MODULE$.unit().value().get();
//            logger.warn(promisesToContext.toString());
////            promisesToContext.put(unitFuture, null);
//        }
//    }
//
    public static class TransformationConstructorInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("scala.concurrent.impl.Promise$Transformation");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onExit(@Advice.This Object thiz) {
            final AbstractSpan<?> context = tracer.getActive();
            if (context != null) {
                logger.warn("==============");
                logger.warn("Constructor " + context + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                logger.warn("Constructor Trans " + (tracer.currentTransaction() == context) + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                logger.warn("==============");
                promisesToContext.put(thiz, context);
                // this span might be ended before the Promise$Transformation#run method starts
                // we have to avoid that this span gets recycled, even in the above mentioned case
                context.incrementReferences();
                // Do no discard branches leading to async operations so not to break span references
                context.setNonDiscardable();
            }
        }

    }

    public static class TransformationSubmitWithValueInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("scala.concurrent.impl.Promise$Transformation");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("submitWithValue").and(returns(named("scala.concurrent.impl.Promise$Transformation")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Object thiz) {
            final AbstractSpan<?> context = tracer.getActive();
            if (context != null) {
                logger.warn("==============");
                logger.warn("SubmitWithValue " + context + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                logger.warn("==============");
                promisesToContext.put(thiz, context);
                // this span might be ended before the Promise$Transformation#run method starts
                // we have to avoid that this span gets recycled, even in the above mentioned case
                context.incrementReferences();
                // Do no discard branches leading to async operations so not to break span references
                context.setNonDiscardable();
            }
        }
    }

    public static class TransformationRunInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("scala.concurrent.impl.Promise$Transformation");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("run").and(returns(void.class));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Object thiz) {
            AbstractSpan<?> context = promisesToContext.remove(thiz);
            if (context != null) {
                logger.warn("==============");
                logger.warn("Enter Run " + context + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                if (tracer.getActive() != context) context.activate();
                // decrements the reference we incremented to avoid that the parent context gets recycled before the promise is run
                // because we have activated it, we can be sure it doesn't get recycled until we deactivate in the OnMethodExit advice
                context.decrementReferences();
                logger.warn("Enter Run After 1 " + context + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                logger.warn("Enter Run After 2 " + context.getReferenceCount() + " on thread " + Thread.currentThread().getId() + " for " + thiz.hashCode());
                logger.warn("==============");
            }
            return context;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onExit(@Advice.Enter @Nullable Object abstractSpanObj) {
            if (abstractSpanObj instanceof AbstractSpan<?>) {
                AbstractSpan<?> context = (AbstractSpan<?>) abstractSpanObj;
                logger.warn("==============");
                logger.warn("Exit Run " + context + " on thread " + Thread.currentThread().getId());
                logger.warn("Exit Run Active 1 " + tracer.getActive() + " on thread " + Thread.currentThread().getId());
                logger.warn("Exit Run Active 2 " + context.getReferenceCount() + " on thread " + Thread.currentThread().getId());
                context.deactivate();
                logger.warn("Exit Run Active 3 " + tracer.getActive() + " on thread " + Thread.currentThread().getId());
                logger.warn("==============");
            }
        }
    }
}
