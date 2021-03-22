/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.context;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

/**
 * Registry that contains in-flight spans/transactions.
 */
public class InFlightRegistry {

    private static final WeakConcurrentSet<AbstractSpan<?>> inFlight = WeakMapSupplier.createSet();

    /**
     * Adds context to `in-flight'
     *
     * @param context span/transaction to mark as 'in-flight'
     */
    public static void inFlightStart(AbstractSpan<?> context) {
        inFlight.add(context);
    }

    /**
     * Removes context from 'in-flight'
     *
     * @param context span/transaction to remove from 'in-flight'
     * @throws IllegalStateException if context is not in-flight
     */
    public static void inFlightEnd(AbstractSpan<?> context) {
        if (!inFlight.remove(context)) {
            throw new IllegalStateException("not in-flight anymore " + context);
        }
    }

    /**
     * Activates context if in-flight
     *
     * @param context span/transaction to activate
     * @return {@literal true} when context has been activated, {@literal false} otherwise
     */
    public static boolean activateInFlight(AbstractSpan<?> context) {
        boolean isInFlight = inFlight.contains(context);
        if (isInFlight) {
            context.activate();
        }
        return isInFlight;
    }

    /**
     * Deactivates context if in-flight
     *
     * @param context span/transaction to deactivate
     * @return {@literal true} when context has been deactivated, {@literal false} otherwise
     */
    public static boolean deactivateInFlight(AbstractSpan<?> context) {
        boolean isInFlight = inFlight.contains(context);
        if (isInFlight) {
            context.deactivate();
        }
        return isInFlight;
    }
}
