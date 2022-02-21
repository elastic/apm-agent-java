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

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DroppedSpanStats implements Iterable<Map.Entry<DroppedSpanStats.StatsKey, DroppedSpanStats.StatsValue>>, Recyclable {

    public static class StatsKey {
        private final String destinationServiceResource;
        private final Outcome outcome;

        public StatsKey(CharSequence destinationServiceResource, Outcome outcome) {
            this.destinationServiceResource = destinationServiceResource.toString();
            this.outcome = outcome;
        }

        public String getDestinationServiceResource() {
            return destinationServiceResource;
        }

        public Outcome getOutcome() {
            return outcome;
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

    public static class StatsValue {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong sum = new AtomicLong(0L);

        public int getCount() {
            return count.get();
        }

        public long getSum() {
            return sum.get();
        }
    }

    private final ConcurrentMap<StatsKey, StatsValue> statsMap = new ConcurrentHashMap<>();

    StatsValue getStats(String destinationServiceResource, Outcome outcome) {
        return statsMap.get(new StatsKey(destinationServiceResource, outcome));
    }

    public void captureDroppedSpan(Span span) {
        StringBuilder resource = span.getContext().getDestination().getService().getResource();
        if (!span.isExit() || resource.length() == 0) {
            return;
        }

        StatsValue stats = getStats(new StatsKey(resource, span.getOutcome()));
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

    private StatsValue getStats(StatsKey statsKey) {
        StatsValue statsValue = statsMap.get(statsKey);
        if (statsValue != null) {
            return statsValue;
        }

        synchronized (this) {
            statsValue = statsMap.get(statsKey);
            if (statsValue != null) {
                return statsValue;
            }
            if (statsMap.size() < 128) {
                statsValue = new StatsValue();
                statsMap.put(statsKey, statsValue);
                return statsValue;
            }
        }
        return null;
    }

    @Override
    public Iterator<Map.Entry<StatsKey, StatsValue>> iterator() {
        return statsMap.entrySet().iterator();
    }

    @Override
    public void resetState() {
        statsMap.clear();
    }
}
