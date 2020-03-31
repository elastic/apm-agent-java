/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.bci.bytebuddy;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;

public class MatcherTimer implements Comparable<MatcherTimer> {

    private final String adviceClass;
    private final AtomicLong totalTypeMatchingDuration = new AtomicLong();
    private final AtomicLong totalMethodMatchingDuration = new AtomicLong();

    public MatcherTimer(String adviceClassName) {
        this.adviceClass = adviceClassName;
    }

    public void addTypeMatchingDuration(long typeMatchingDuration) {
        totalTypeMatchingDuration.addAndGet(typeMatchingDuration);
    }

    public void addMethodMatchingDuration(long methodMatchingDuration) {
        totalMethodMatchingDuration.addAndGet(methodMatchingDuration);
    }

    @Override
    public int compareTo(MatcherTimer o) {
        return Long.compare(o.getTotalTime(), getTotalTime());
    }

    public long getTotalTime() {
        return totalTypeMatchingDuration.get() + totalMethodMatchingDuration.get();
    }

    public static String getTableHeader() {
        return String.format("| %-50s | %-15s | %-15s |", "Advice name", "Type ns", "Method ns");
    }

    @Override
    public String toString() {
        return String.format("| %-50s | %,15d | %,15d |", getSimpleClassName(adviceClass),
            totalTypeMatchingDuration.get(), totalMethodMatchingDuration.get());
    }

    @Nonnull
    private static String getSimpleClassName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

}
