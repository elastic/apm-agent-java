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
package co.elastic.apm.agent.report;

import java.util.concurrent.atomic.AtomicLongArray;

public class ReportingEventCounter {

    private final AtomicLongArray counters;

    public ReportingEventCounter() {
        ReportingEvent.ReportingEventType[] types = ReportingEvent.ReportingEventType.values();
        counters = new AtomicLongArray(types.length);
    }

    public ReportingEventCounter(ReportingEventCounter toCopy) {
        ReportingEvent.ReportingEventType[] types = ReportingEvent.ReportingEventType.values();
        counters = new AtomicLongArray(types.length);
        for (int i = 0; i < counters.length(); i++) {
            counters.set(i, toCopy.counters.get(i));
        }
    }

    public void reset() {
        for (int i = 0; i < counters.length(); i++) {
            counters.set(i, 0);
        }
    }

    public void increment(ReportingEvent.ReportingEventType type) {
        counters.incrementAndGet(type.ordinal());
    }

    public void add(ReportingEvent.ReportingEventType type, long count) {
        counters.addAndGet(type.ordinal(), count);
    }

    public long getCount(ReportingEvent.ReportingEventType type) {
        return counters.get(type.ordinal());
    }

    public long getTotalCount() {
        long sum = 0;
        for (int i = 0; i < counters.length(); i++) {
            sum += counters.get(i);
        }
        return sum;
    }
    
    public void addAll(ReportingEventCounter other) {
        for (int i = 0; i < counters.length(); i++) {
            counters.addAndGet(i, other.counters.get(i));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReportingEventCounter[");
        boolean isFirst = true;
        for (ReportingEvent.ReportingEventType type : ReportingEvent.ReportingEventType.values()) {
            long count = getCount(type);
            if (count > 0) {
                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;
                sb.append(type.toString()).append('=').append(count);
            }
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReportingEventCounter that = (ReportingEventCounter) o;
        for (int i = 0; i < counters.length(); i++) {
            if (counters.get(i) != that.counters.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        //implementation copied form Arrays.hashcode
        int result = 1;
        for (int i = 0; i < counters.length(); i++) {
            long element = counters.get(i);
            int elementHash = (int) (element ^ (element >>> 32));
            result = 31 * result + elementHash;
        }
        return result;
    }

}
