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

import co.elastic.apm.agent.impl.context.ServiceTarget;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.util.CharSequenceUtils;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DroppedSpanStats implements Iterable<Map.Entry<DroppedSpanStats.StatsKey, DroppedSpanStats.Stats>>, Recyclable {

    public static class StatsKey implements Recyclable {

        // attribute is technically nullable, but not after 'init' method is called
        @Nullable
        private String serviceType;

        private final StringBuilder serviceName;

        private final StringBuilder destinationResource;

        private Outcome outcome;

        private int cachedHashCode;

        public StatsKey() {
            this.serviceName = new StringBuilder();
            this.destinationResource = new StringBuilder();
            this.outcome = Outcome.UNKNOWN;
            cachedHashCode = Integer.MIN_VALUE;
        }

        public StatsKey init(ServiceTarget serviceTarget, Outcome outcome) {
            resetState();
            // we have to use a copy as argument is mutable will be recycled
            this.serviceType = Objects.requireNonNull(serviceTarget.getType());
            CharSequence name = serviceTarget.getName();
            if(name != null) {
                this.serviceName.append(name);
            }
            this.destinationResource.append(Objects.requireNonNull(serviceTarget.getDestinationResource()));
            this.outcome = outcome;
            return this;
        }

        @Nullable
        public CharSequence getDestinationServiceResource() {
            return destinationResource;
        }

        public String getServiceType() {
            return Objects.requireNonNull(serviceType);
        }

        @Nullable
        public CharSequence getServiceName() {
            return serviceName;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        @Override
        public void resetState() {
            serviceType = null;
            serviceName.setLength(0);
            destinationResource.setLength(0);
            outcome = Outcome.UNKNOWN;
            cachedHashCode = Integer.MIN_VALUE;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StatsKey statsKey = (StatsKey) o;

            if (serviceType != null ? !serviceType.equals(statsKey.serviceType) : statsKey.serviceType != null)
                return false;
            if (!CharSequenceUtils.equals(serviceName, statsKey.serviceName)) return false;
            if (!CharSequenceUtils.equals(destinationResource, statsKey.destinationResource)) return false;
            return outcome == statsKey.outcome;
        }

        @Override
        public int hashCode() {
            // caching hashcode as multiple calls are expected to this method and we can avoid expensive re-computation
            if (cachedHashCode != Integer.MIN_VALUE) {
                return cachedHashCode;
            }
            int result = serviceType != null ? serviceType.hashCode() : 0;
            result = 31 * result + CharSequenceUtils.hashCode(serviceName);
            result = 31 * result + CharSequenceUtils.hashCode(destinationResource);
            result = 31 * result + outcome.hashCode();
            cachedHashCode = result;
            return result;
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

    private static final ObjectPool<Stats> statsObjectPool = QueueBasedObjectPool.<Stats>ofRecyclable(new MpmcAtomicArrayQueue<Stats>(512), false, new Allocator<Stats>() {
        @Override
        public Stats createInstance() {
            return new Stats();
        }
    });

    private final ConcurrentMap<StatsKey, Stats> statsMap = new ConcurrentHashMap<>();

    // only used during testing
    @Nullable
    Stats getStats(String serviceType, @Nullable String serviceName, Outcome outcome){
        ServiceTarget st = new ServiceTarget().withType(serviceType).withName(serviceName);
        return statsMap.get(new StatsKey().init(st, outcome));
    }

    public void captureDroppedSpan(Span span) {
        ServiceTarget serviceTarget = span.getContext().getServiceTarget();
        if (!span.isExit() || !serviceTarget.hasContent() ) {
            return;
        }

        Stats stats = getOrCreateStats(serviceTarget, span.getOutcome());

        if (span.isComposite()) {
            stats.count.addAndGet(span.getComposite().getCount());
        } else {
            stats.count.incrementAndGet();
        }
        stats.sum.addAndGet(span.getDuration());
    }

    private Stats getOrCreateStats(ServiceTarget serviceTarget, Outcome outcome) {
        StatsKey statsKey = statsKeyObjectPool.createInstance().init(serviceTarget, outcome);
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
