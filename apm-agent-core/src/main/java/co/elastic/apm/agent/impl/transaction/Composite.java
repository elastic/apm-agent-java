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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Composite implements Recyclable {

    private final AtomicInteger count = new AtomicInteger(0);

    private final AtomicLong sum = new AtomicLong(0L);

    @Nullable
    private String compressionStrategy;

    public boolean init(long sum, String compressionStrategy) {
        if (!this.count.compareAndSet(0, 1)) {
            return false;
        }
        this.sum.set(sum);
        this.compressionStrategy = compressionStrategy;
        return true;
    }

    public int getCount() {
        return count.get();
    }

    public void increaseCount() {
        count.incrementAndGet();
    }

    public long getSum() {
        return sum.get();
    }

    public double getSumMs() {
        return sum.get() / 1000.0;
    }

    public void increaseSum(long delta) {
        this.sum.addAndGet(delta);
    }

    public String getCompressionStrategy() {
        return compressionStrategy;
    }

    @Override
    public void resetState() {
        this.count.set(0);
        this.sum.set(0L);
        this.compressionStrategy = null;
    }
}
