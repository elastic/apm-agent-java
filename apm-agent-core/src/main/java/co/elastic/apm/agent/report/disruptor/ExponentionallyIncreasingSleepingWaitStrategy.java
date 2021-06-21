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
package co.elastic.apm.agent.report.disruptor;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;

import java.util.concurrent.locks.LockSupport;

/**
 * Sleeping strategy that sleeps (<code>LockSupport.parkNanos(n)</code>) for an linearly increasing
 * number of nanos while the {@link com.lmax.disruptor.EventProcessor}s are waiting on a barrier.
 * <p>
 * This strategy consumes very little CPU resources.
 * However, latency spikes up to {@link #sleepTimeNsMax} will occur.
 * It will also reduce the impact on the producing thread as it will not need signal any conditional variables to wake up the event handling
 * thread.
 * </p>
 * <p>
 * The agent does not require to transfer events with low latency from producers to the consumer.
 * The primary concerns are low latency for adding events (to not block application threads) and low CPU consumption.
 * It does not matter if handling the events is a bit delayed.
 * The only caveat is that sudden traffic spikes after quiet periods can lead to loosing events.
 * If that should become a problem, take a look at the approaches outlined in
 * https://github.com/LMAX-Exchange/disruptor/issues/246 and https://github.com/census-instrumentation/opencensus-java/pull/1618.
 * In situations where the consumer is overloaded (always busy and awake) this wait strategy actually has very good latency
 * because consumers don't have to signal.
 * </p>
 */
public final class ExponentionallyIncreasingSleepingWaitStrategy implements WaitStrategy {

    private final int sleepTimeNsStart;
    private final int sleepTimeNsMax;

    public ExponentionallyIncreasingSleepingWaitStrategy(int sleepTimeNsStart, int sleepTimeNsMax) {
        this.sleepTimeNsStart = sleepTimeNsStart;
        this.sleepTimeNsMax = sleepTimeNsMax;
    }

    @Override
    public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier) throws AlertException {
        long availableSequence;
        int currentSleep = sleepTimeNsStart;

        while ((availableSequence = dependentSequence.get()) < sequence) {
            currentSleep = applyWaitMethod(barrier, currentSleep);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
    }

    private int applyWaitMethod(final SequenceBarrier barrier, int currentSleep) throws AlertException {
        barrier.checkAlert();

        if (currentSleep < sleepTimeNsMax) {
            LockSupport.parkNanos(currentSleep);
            return currentSleep * 2;
        } else {
            LockSupport.parkNanos(sleepTimeNsMax);
            return currentSleep;
        }
    }
}
