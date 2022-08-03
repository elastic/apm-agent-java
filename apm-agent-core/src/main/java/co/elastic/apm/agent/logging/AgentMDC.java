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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.sdk.state.GlobalState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent internal MDC for log-correlation to be used when instrumented library does not provide one.
 */
@GlobalState
public class AgentMDC {

    /**
     * This MDC is currently used with 3 key/values: trace,transaction and error IDs.
     */
    private static final int INITIAL_CAPACITY = 4;

    private static final ThreadLocal<Map<String, String>> tlm = new ThreadLocal<Map<String, String>>();

    private AgentMDC() {
    }

    public static void put(@Nullable String key, @Nullable String value) {
        if (key == null || value == null) {
            return;
        }
        Map<String, String> map = tlm.get();
        if (map == null) {
            map = new HashMap<String, String>(INITIAL_CAPACITY);
            tlm.set(map);
        }
        map.put(key, value);
    }

    public static void remove(@Nullable String key) {
        if (key == null) {
            return;
        }
        Map<String, String> entries = tlm.get();
        if (entries != null) {
            entries.remove(key);
        }
    }

    /**
     * Get the MDC entries, the returned map should not escape the current thread as the map implementation is not
     * thread-safe and thus concurrent modification is not supported.
     *
     * @return map of MDC entries
     */
    public static Map<String, String> getEntries() {
        Map<String, String> entries = tlm.get();
        return entries == null ? Collections.<String, String>emptyMap() : entries;
    }

}
