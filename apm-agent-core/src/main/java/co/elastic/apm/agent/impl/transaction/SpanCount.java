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

import java.util.concurrent.atomic.AtomicInteger;

public class SpanCount implements Recyclable {

    private final AtomicInteger dropped = new AtomicInteger(0);
    private final AtomicInteger reported = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);

    public AtomicInteger getDropped() {
        return dropped;
    }

    public AtomicInteger getReported() {
        return reported;
    }

    public AtomicInteger getTotal() {
        return total;
    }

    public boolean isSpanLimitReached(int maxSpans) {
        return maxSpans <= total.get() - dropped.get();
    }

    @Override
    public void resetState() {
        dropped.set(0);
        reported.set(0);
        total.set(0);
    }
}
