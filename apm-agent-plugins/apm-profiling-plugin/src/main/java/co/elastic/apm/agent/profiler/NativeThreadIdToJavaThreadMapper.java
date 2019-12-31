/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class NativeThreadIdToJavaThreadMapper {

    private final WeakConcurrentMap<Thread, Long> threadToNativeThread = new WeakConcurrentMap<Thread, Long>(false);
    private final Set<Long> nativeThreadIds = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private AsyncProfiler asyncProfiler = AsyncProfiler.getInstance();

    public long getNativeThreadId(Thread thread) {
        Long nativeThreadId = threadToNativeThread.get(thread);
        if (nativeThreadId == null) {
            do {
                nativeThreadId = asyncProfiler.getNativeThreadId(thread);
                LockSupport.parkNanos(200);
            } while (nativeThreadId == -1);
            if (threadToNativeThread.putIfAbsent(thread, nativeThreadId) == null) {
                nativeThreadIds.add(nativeThreadId);
            }
        }
        return nativeThreadId;
    }

    public long[] getSortedNativeThreadIds() {
        Set<Long> nativeThreadIds = new TreeSet<>();
        for (Map.Entry<Thread, Long> entry : threadToNativeThread) {
            nativeThreadIds.add(entry.getValue());
        }
        long[] result = new long[nativeThreadIds.size()];
        int i = 0;
        for (Long nativeThreadId : nativeThreadIds) {
            result[i] = nativeThreadId;
            i++;
        }
        return result;
    }
}
