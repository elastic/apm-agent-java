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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

/**
 * This clock makes sure that each {@link Span} and {@link Transaction} uses a consistent clock
 * which does not drift in case of NTP updates or leap seconds.
 * <p>
 * The clock is initialized with wall clock time when the transaction starts and from there on
 * uses {@link System#nanoTime()} in order to be able to measure durations.
 * {@link System#currentTimeMillis()}, which uses wall clock time is not suitable for measuring durations because of the aforementioned
 * possibility of clock drifts.
 * </p>
 */
public class EpochTickClock implements Recyclable {

    private long nanoTimeOffsetToEpoch;

    /**
     * Initializes the clock by aligning the {@link #nanoTimeOffsetToEpoch offset} with the offset of another clock.
     *
     * @param other the other clock, which has already been initialized
     */
    public void init(EpochTickClock other) {
        this.nanoTimeOffsetToEpoch = other.nanoTimeOffsetToEpoch;
    }

    /**
     * Initializes and calibrates the clock based on wall clock time
     *
     * @return the epoch microsecond timestamp at initialization time
     */
    public long init() {
        return init(System.currentTimeMillis() * 1000, System.nanoTime());
    }

    void init(long nanoTimeOffsetToEpoch) {
        this.nanoTimeOffsetToEpoch = nanoTimeOffsetToEpoch;
    }

    /**
     * Initializes and calibrates the clock based on wall clock time
     *
     * @param epochMicrosWallClock the current timestamp in microseconds since epoch, based on wall clock time
     * @param nanoTime             the current nanosecond ticks (mostly {@link System#nanoTime()}
     * @return the epoch microsecond timestamp at initialization time
     */
    public long init(long epochMicrosWallClock, long nanoTime) {
        nanoTimeOffsetToEpoch = epochMicrosWallClock * 1_000 - nanoTime;
        return epochMicrosWallClock;
    }

    public long getEpochMicros() {
        return getEpochMicros(System.nanoTime());
    }

    public long getEpochMicros(final long nanoTime) {
        return (nanoTime + nanoTimeOffsetToEpoch) / 1000;
    }

    @Override
    public void resetState() {
        nanoTimeOffsetToEpoch = 0;
    }

    long getOffset() {
        return nanoTimeOffsetToEpoch;
    }
}
