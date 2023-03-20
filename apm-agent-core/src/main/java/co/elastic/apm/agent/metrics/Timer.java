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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This timer track the total time and the count of invocations so that it allows for calculating weighted averages.
 */
public class Timer implements Recyclable {
    private static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);

    private AtomicLong totalTime = new AtomicLong();
    private AtomicLong count = new AtomicLong();

    public void update(long durationUs) {
        update(durationUs, 1);
    }

    public void update(long durationUs, long count) {
        this.totalTime.addAndGet(durationUs);
        this.count.addAndGet(count);
    }

    public long getTotalTimeUs() {
        return totalTime.get();
    }

    public double getTotalTimeMs() {
        return totalTime.get() / MS_IN_MICROS;
    }

    public long getCount() {
        return count.get();
    }

    public boolean hasContent() {
        return count.get() > 0;
    }

    @Override
    public void resetState() {
        totalTime.set(0);
        count.set(0);
    }
}
