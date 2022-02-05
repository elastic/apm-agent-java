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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InstrumentationUsageUtil {

    private static final Set<ElasticApmInstrumentation> allInstrumentations = new HashSet<>();

    private static final ConcurrentMap<ElasticApmInstrumentation, Boolean> usedInstrumentations = new ConcurrentHashMap<>();

    static void reset() {
        allInstrumentations.clear();
        usedInstrumentations.clear();
    }

    public static void addInstrumentation(ElasticApmInstrumentation instrumentation) {
        allInstrumentations.add(instrumentation);
    }

    public static void addUsedInstrumentation(ElasticApmInstrumentation instrumentation) {
        usedInstrumentations.put(instrumentation, Boolean.TRUE);
    }

    public static Collection<String> getUsedInstrumentationGroups() {
        Set<String> usedInstrumentationGroups = new TreeSet<>();
        for (ElasticApmInstrumentation instrumentation : allInstrumentations) {
            if (!usedInstrumentations.containsKey(instrumentation)) {
                continue;
            }
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
}
