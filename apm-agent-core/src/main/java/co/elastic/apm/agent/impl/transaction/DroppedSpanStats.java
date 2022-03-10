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

import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DroppedSpanStats implements Iterable<Map.Entry<DroppedSpanStats.StatsKey, DroppedSpanStats.Stats>>, Recyclable {

    public static class StatsKey implements Recyclable {
        private String destinationServiceResource;
        private Outcome outcome;

        public StatsKey() {

        }

        public StatsKey init(CharSequence destinationServiceResource, Outcome outcome) {
            this.destinationServiceResource = destinationServiceResource.toString();
            this.outcome = outcome;
            return this;
        }

        public String getDestinationServiceResource() {
            return destinationServiceResource;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        @Override
        public void resetState() {
            destinationServiceResource = null;
            outcome = null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatsKey statsKey = (StatsKey) o;
            return destinationServiceResource.equals(statsKey.destinationServiceResource) && outcome == statsKey.outcome;
        }

        @Override
        public int hashCode() {
            return Objects.hash(destinationServiceResource, outcome);
        }
    }

    public static class Stats implements Recyclable {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong sum = new AtomicLong(0L);

        public int getCount() {
            return count.get();
        }

        public long getSum() {
            return sum.get();
        }

        @Override
        public void resetState() {
            count.set(0);
            sum.set(0L);
        }
    }

    private static final ObjectPool<StatsKey> statsKeyObjectPool = QueueBasedObjectPool.<StatsKey>ofRecyclable(new MpmcAtomicArrayQueue<StatsKey>(512), false, new Allocator<StatsKey>() {
        @Override
        public StatsKey createInstance() {
            return new StatsKey();
        }
    });

    private static ObjectPool<Stats> statsObjectPool = QueueBasedObjectPool.<Stats>ofRecyclable(new MpmcAtomicArrayQueue<Stats>(512), false, new Allocator<Stats>() {
        @Override
        public Stats createInstance() {
            return new Stats();
        }
    });

    private final ConcurrentMap<StatsKey, Stats> statsMap = new ConcurrentHashMap<>();

    //only used during testing
    Stats getStats(String destinationServiceResource, Outcome outcome) {
        return statsMap.get(new StatsKey().init(destinationServiceResource, outcome));
    }

    public void captureDroppedSpan(Span span) {
        StringBuilder resource = span.getContext().getDestination().getService().getResource();
        if (!span.isExit() || resource.length() == 0) {
            return;
        }

        StatsKey statsKey = statsKeyObjectPool.createInstance().init(resource, span.getOutcome());
        Stats stats = getOrCreateStats(statsKey);
        if (stats == null) {
            return;
        }

        if (span.isComposite()) {
            stats.count.addAndGet(span.getComposite().getCount());
        } else {
            stats.count.incrementAndGet();
        }
        stats.sum.addAndGet(span.getDuration());
    }

    private Stats getOrCreateStats(StatsKey statsKey) {
        Stats stats = statsMap.get(statsKey);
        if (stats != null || statsMap.size() > 127) {
            statsKeyObjectPool.recycle(statsKey);
            return stats;
        }

        stats = statsObjectPool.createInstance();

        Stats oldStats = statsMap.putIfAbsent(statsKey, stats);
        if (oldStats != null) {
            statsKeyObjectPool.recycle(statsKey);
            statsObjectPool.recycle(stats);
            return oldStats;
        }

        return stats;
    }

    @Override
    public Iterator<Map.Entry<StatsKey, Stats>> iterator() {
        return statsMap.entrySet().iterator();
    }

    @Override
    public void resetState() {
        for (Map.Entry<StatsKey, Stats> e : statsMap.entrySet()) {
            statsKeyObjectPool.recycle(e.getKey());
            statsObjectPool.recycle(e.getValue());
        }
        statsMap.clear();
    }
}
