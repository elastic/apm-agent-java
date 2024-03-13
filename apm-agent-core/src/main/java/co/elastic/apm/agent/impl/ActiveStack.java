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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateWrapper;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Manages a thread's tracing-context activation state.
 * Instances of this class should be used as thread-locals.
 * Accordingly, it is implemented without taking any thread-safety considerations into account.
 */
class ActiveStack {

    private static final Logger logger = LoggerFactory.getLogger(ActiveStack.class);

    private final int stackMaxDepth;

    /**
     * In case the stack reaches its maximum allowed capacity, it won't allow for activation of any more spans.
     * Instead, it would only keep this state of the size of the overflow. This enables both limited logging of only once
     * per overflowing and the ability to restore proper activation/deactivation capabilities for the allowed stack size.
     */
    private long overflowCounter = 0;

    /**
     * Maintains a stack of all the activated spans/contexts.
     * This way it's easy to retrieve the bottom of the stack (the transaction).
     * Also, the caller does not have to keep a reference to the previously active span, as that is maintained by the stack.
     * This makes activating a span allocation-free (assuming the stack has enough pre-allocated slots available).
     */
    private final Deque<TraceStateImpl<?>> activeContextStack = new ArrayDeque<TraceStateImpl<?>>();

    private final EmptyTraceState emptyContext;

    ActiveStack(int stackMaxDepth, EmptyTraceState emptyContextForTracer) {
        this.stackMaxDepth = stackMaxDepth;
        this.emptyContext = emptyContextForTracer;
    }

    /**
     * @return the current context, potentially empty when no span, transaction or baggage is currently active.
     */

    public TraceStateImpl<?> currentContext() {
        TraceStateImpl<?> current = activeContextStack.peek();

        // When the active context is wrapped, the wrapper should be transparent to the caller, thus we always return
        // the underlying wrapped context.
        if (current instanceof TraceStateWrapper) {
            return ((TraceStateWrapper<?>) current).getWrappedContext();
        }
        return current != null ? current : emptyContext;
    }

    boolean activate(TraceStateImpl<?> context, List<ActivationListener> activationListeners) {
        if (logger.isDebugEnabled()) {
            logger.debug("Activating {} on thread {}", context, Thread.currentThread().getId());
        }

        if (activeContextStack.size() == stackMaxDepth) {
            if (overflowCounter == 0) {
                logger.error(String.format("Activation stack depth reached its maximum - %s. This is likely related to activation" +
                        " leak. Current transaction: %s", stackMaxDepth, currentContext().getTransaction()),
                    new Throwable("Stack of threshold-crossing activation: ")
                );
            }
            overflowCounter++;
            return false;
        }

        context.incrementReferences();
        AbstractSpanImpl<?> span = context.getSpan();
        if (span != null) {
            triggerActivationListeners(span, true, activationListeners);
        }

        activeContextStack.push(context);
        return true;
    }

    boolean deactivate(TraceStateImpl<?> context, List<ActivationListener> activationListeners, boolean assertionsEnabled) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deactivating {} on thread {}", context, Thread.currentThread().getId());
        }

        if (overflowCounter > 0) {
            overflowCounter--;
            return false;
        }

        TraceStateImpl<?> activeContext = currentContext();
        activeContextStack.remove();

        try {
            assertIsActive(context, activeContext, assertionsEnabled);

            AbstractSpanImpl<?> span = context.getSpan();
            if (null != span) {
                triggerActivationListeners(span, false, activationListeners);
            }
        } finally {
            context.decrementReferences();
        }
        return true;
    }

    private void triggerActivationListeners(AbstractSpanImpl<?> span, boolean isActivate, List<ActivationListener> activationListeners) {
        for (int i = 0, size = activationListeners.size(); i < size; i++) {
            ActivationListener listener = activationListeners.get(i);
            try {
                if (isActivate) {
                    listener.beforeActivate(span);
                } else {
                    // `this` is guaranteed to not be recycled yet as the reference count is only decremented after this method has executed
                    listener.afterDeactivate(span);
                }
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#{}", listener.getClass().getSimpleName(), isActivate ? "beforeActivate" : "afterDeactivate", t);
            }
        }
    }

    private void assertIsActive(TraceStateImpl<?> context, @Nullable TraceStateImpl<?> currentlyActive, boolean assertionsEnabled) {
        if (context != currentlyActive) {
            logger.warn("Deactivating a context ({}) which is not the currently active one ({}). " +
                "This can happen when not properly deactivating a previous span or context.", context, currentlyActive);

            if (assertionsEnabled) {
                throw new AssertionError("Deactivating a context that is not the active one");
            }
        }
    }

    /**
     * Lazily wraps the currently active context if required, wrapper instance is cached with wrapperClass as key.
     * Wrapping is transparently handled by {@link #currentContext()}.
     *
     * @param wrapperClass wrapper type
     * @param wrapFunction wrapper creation function
     * @param <T>          wrapper type
     * @return newly (or previously) created wrapper
     */
    <T extends TraceStateImpl<T>> T wrapActiveContextIfRequired(Class<T> wrapperClass, Callable<T> wrapFunction, int approximateContextSize) {

        // the current context might be either a "regular" one or a "wrapped" one if it has already been wrapped
        TraceStateImpl<?> current = activeContextStack.peek();

        Objects.requireNonNull(current, "active context required for wrapping");
        TraceStateWrapper<?> wrapper;
        if (current instanceof TraceStateWrapper) {
            wrapper = (TraceStateWrapper<?>) current;
        } else {
            wrapper = new TraceStateWrapper<>(approximateContextSize, current);
        }
        T wrapped = wrapper.wrapIfRequired(wrapperClass, wrapFunction);

        // replace the currently active on the stack, however currentContext() will make sure to return the original
        // context in order to keep wrapping transparent.
        activeContextStack.remove();
        activeContextStack.push(wrapper);

        return wrapped;
    }
}
