/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;

import java.util.concurrent.atomic.AtomicInteger;

public class Dropped implements Recyclable {

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    private final AtomicInteger total = new AtomicInteger();

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    public int getTotal() {
        return total.get();
    }

    /**
     * Increments the number of spans that have been dropped by the agent recording the transaction.
     */
    public Dropped increment() {
        this.total.incrementAndGet();
        return this;
    }

    @Override
    public void resetState() {
        total.set(0);
    }
}
