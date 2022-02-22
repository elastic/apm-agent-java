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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.bci.bytebuddy.MatcherTimer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InstrumentationStats {

    private final Set<ElasticApmInstrumentation> allInstrumentations = new HashSet<>();

    private final ConcurrentMap<ElasticApmInstrumentation, Boolean> usedInstrumentations = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, MatcherTimer> matcherTimers = new ConcurrentHashMap<>();

    private boolean measureMatching = false;

    void reset() {
        allInstrumentations.clear();
        usedInstrumentations.clear();
        matcherTimers.clear();
        measureMatching = false;
    }

    void addInstrumentation(ElasticApmInstrumentation instrumentation) {
        allInstrumentations.add(instrumentation);
    }

    void addUsedInstrumentation(ElasticApmInstrumentation instrumentation) {
        usedInstrumentations.put(instrumentation, Boolean.TRUE);
    }

    Collection<String> getUsedInstrumentationGroups() {
        Set<String> usedInstrumentationGroups = new TreeSet<>();
        for (ElasticApmInstrumentation instrumentation : usedInstrumentations.keySet()) {
            usedInstrumentationGroups.addAll(instrumentation.getInstrumentationGroupNames());
        }
        for (ElasticApmInstrumentation instrumentation : allInstrumentations) {
            if (usedInstrumentations.containsKey(instrumentation)) {
                continue;
            }
            Collection<String> instrumentationGroups = instrumentation.getInstrumentationGroupNames();
            if (usedInstrumentationGroups.containsAll(instrumentationGroups)) {
                continue;
            }
            usedInstrumentationGroups.removeAll(instrumentationGroups);
        }

        return usedInstrumentationGroups;
    }

    MatcherTimer getOrCreateTimer(Class<? extends ElasticApmInstrumentation> adviceClass) {
        final String name = adviceClass.getName();
        MatcherTimer timer = matcherTimers.get(name);
        if (timer == null) {
            matcherTimers.putIfAbsent(name, new MatcherTimer(name));
            return matcherTimers.get(name);
        } else {
            return timer;
        }
    }

    long getTotalMatcherTime() {
        long totalTime = 0;
        for (MatcherTimer value : matcherTimers.values()) {
            totalTime += value.getTotalTime();
        }
        return totalTime;
    }

    Collection<MatcherTimer> getMatcherTimers() {
        return matcherTimers.values();
    }

    public void setMeasureMatching(boolean measureMatching) {
        this.measureMatching = measureMatching;
    }

    public boolean shouldMeasureMatching() {
        return measureMatching;
    }
}
