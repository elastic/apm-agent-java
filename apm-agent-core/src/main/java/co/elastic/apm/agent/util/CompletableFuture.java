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
package co.elastic.apm.agent.util;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A simple implementation for Java-7-compatible CompletableFuture, which supports {@code null} values.
 * @param <V>
 */
public class CompletableFuture<V> implements Future<V> {

    private final Object LOCK = new Object();

    @Nullable
    volatile V value;

    volatile boolean completed = false;
    volatile boolean cancelled = false;

    /**
     * {@inheritDoc}
     * @param mayInterruptIfRunning this value has no effect in this
     *      * implementation because interrupts are not used to control
     *      * processing.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // first try to exit early without grabbing the lock if possible
        if (cancelled || completed) {
            return false;
        }
        synchronized (LOCK) {
            // repeat this check to guarantee atomicity
            if (cancelled || completed) {
                return false;
            }
            cancelled = true;
            LOCK.notifyAll();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        // as per the javadoc of java.util.concurrent.Future#cancel()
        return cancelled || completed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public V get() throws InterruptedException, ExecutionException {
        // first try to exit early without grabbing the lock if possible
        if (completed) {
            return value;
        }
        checkCancellation();
        synchronized (LOCK) {
            // repeat this check to guarantee atomicity
            if (completed) {
                return value;
            }
            checkCancellation();
            LOCK.wait();
            checkCancellation();
            if (!completed) {
                throw new ExecutionException("Wait ended without being cancelled", new IllegalStateException("Future is not completed"));
            }
            return value;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public V get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        // first try to exit early without grabbing the lock if possible
        if (completed) {
            return value;
        }
        checkCancellation();
        synchronized (LOCK) {
            // repeat this check to guarantee atomicity
            if (completed) {
                return value;
            }
            checkCancellation();
            LOCK.wait(unit.toMillis(timeout));
            checkCancellation();
            if (!completed) {
                throw new TimeoutException();
            }
            return value;
        }
    }

    private void checkCancellation() {
        if (cancelled) {
            throw new CancellationException("CompletableFuture has been cancelled before being completed");
        }
    }

    /**
     * If not already completed, sets the value returned by {@link #get()} and related methods to the given value.
     * If invoked on a cancelled future, this method has no effect.
     *
     * @param value the result value
     * @return {@code true} if this invocation actually sets the {@link #value}, else {@code false}
     */
    public boolean complete(@Nullable V value) {
        // first try to exit early without grabbing the lock if possible
        if (cancelled || completed) {
            return false;
        }
        synchronized (LOCK) {
            // repeat this check to guarantee atomicity
            if (cancelled || completed) {
                return false;
            }
            this.value = value;
            this.completed = true;
            LOCK.notifyAll();
            return true;
        }
    }
}
