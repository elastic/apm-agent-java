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
import co.elastic.apm.agent.profiler.collections.Long2ObjectHashMap;
import co.elastic.apm.agent.profiler.collections.LongHashSet;
import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Map;

public class NativeThreadIdToJavaThreadMapper {

    private final DetachedThreadLocal<Long> threadToNativeThread = new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.INLINE);
    private final Long2ObjectHashMap<WeakReference<Thread>> threadIdToThread = new Long2ObjectHashMap<WeakReference<Thread>>();
    private final Object lock = new Object();

    /**
     * Returns the native thread id of the current thread
     */
    public long getNativeThreadId() {
        Long nativeThreadId = threadToNativeThread.get();
        if (nativeThreadId == null) {
            nativeThreadId = AsyncProfiler.getInstance().getNativeThreadId();
            threadToNativeThread.set(nativeThreadId);
            synchronized (lock) {
                threadIdToThread.put(nativeThreadId, new WeakReference<Thread>(Thread.currentThread()));
            }
        }
        return nativeThreadId;
    }

    @Nullable
    public Thread get(long nativeThreadId) {
        // syncronization should not be a bottleneck here
        // only when seeing a thread for the first time in getNativeThreadId, there is a small chance that application threads have to wait
        synchronized (lock) {
            WeakReference<Thread> threadRef = threadIdToThread.get(nativeThreadId);
            if (threadRef != null) {
                return threadRef.get();
            }
            return null;
        }
    }

    /**
     * Removes the mapping from native thread id to {@link Thread} for threads that have already been garbage collected.
     * <p>
     * NOTE: Don't call this method from an application thread.
     * </p>
     */
    public void expungeStaleEntries() {
        synchronized (lock) {
            for (Long2ObjectHashMap<WeakReference<Thread>>.EntryIterator iterator = threadIdToThread.entrySet().iterator(); iterator.hasNext(); ) {
                if (iterator.next().getValue().get() == null) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Returns the native thread ids of all threads for which {@link #getNativeThreadId()} has been called and which are not GC'ed yet.
     */
    public LongHashSet getNativeThreadIds() {
        WeakConcurrentMap<Thread, Long> backingMap = threadToNativeThread.getBackingMap();
        LongHashSet nativeThreadIds = new LongHashSet(backingMap.approximateSize());
        for (Map.Entry<Thread, Long> entry : backingMap) {
            nativeThreadIds.add(entry.getValue());
        }
        return nativeThreadIds;
    }
}
