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
package co.elastic.apm.agent.bci.bytebuddy;

import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.apm.agent.util.ClassLoaderUtils;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EntryWeigher;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.pool.TypePool;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches type descriptions of all class loaders in a global cache with a limited size and a LRU eviction policy.
 * In addition to that, it evicts entries that haven't been accessed recently (see {@link #scheduleEntryEviction(long)}).
 * The default size of the cache targets to allocate {@link #DEFAULT_TARGET_PERCENT_OF_HEAP} of the committed heap,
 * at least {@link #DEFAULT_MIN_CACHE_SIZE} and max {@link #DEFAULT_MAX_CACHE_SIZE}.
 */
public class LruTypePoolCache extends AgentBuilder.PoolStrategy.WithTypePoolCache {

    /**
     * The average size of a {@link TypePool.Resolution} is roughly 16kb.
     * The size estimate is based on a heap dump analysis with a full cache.
     * However, it can vary by application, so this is just a rough estimate.
     */
    static final int AVERAGE_SIZE_OF_TYPE_RESOLUTION = (int) ByteValue.of("16kb").getBytes();
    private static final int DEFAULT_MIN_CACHE_SIZE = (int) ByteValue.of("512kb").getBytes() / AVERAGE_SIZE_OF_TYPE_RESOLUTION;
    private static final int DEFAULT_MAX_CACHE_SIZE = (int) ByteValue.of("10mb").getBytes() / AVERAGE_SIZE_OF_TYPE_RESOLUTION;
    private static final double DEFAULT_TARGET_PERCENT_OF_HEAP = 0.01;
    private static final long DEFAULT_STALE_ENTRY_MAX_AGE_MS = TimeUnit.SECONDS.toMillis(60);

    private final int maxCacheSize;
    /*
     * Wrapped in a SoftReference so that the whole cache can be cleared if the JVM is under memory pressure
     */
    private final AtomicReference<SoftReference<ConcurrentMap<String, ResolutionsByClassLoader>>> sharedCache;
    private final WeakConcurrentMap<ClassLoader, TypePool.CacheProvider> cacheProviders;
    private final ScheduledThreadPoolExecutor executor;

    /**
     * Creates a new type locator that creates {@link TypePool}s but provides a custom {@link TypePool.CacheProvider}.
     * Uses a default size for the cache that targets to allocate {@link #DEFAULT_TARGET_PERCENT_OF_HEAP} of the committed heap,
     * at least {@link #DEFAULT_MIN_CACHE_SIZE} and max {@link #DEFAULT_MAX_CACHE_SIZE}.
     *
     * @param readerMode   The reader mode to use for parsing a class file.
     */
    public LruTypePoolCache(TypePool.Default.ReaderMode readerMode) {
        this(readerMode, LruTypePoolCache.cacheSizeForPercentageOfCommittedHeap(
            LruTypePoolCache.DEFAULT_MIN_CACHE_SIZE,
            LruTypePoolCache.DEFAULT_MAX_CACHE_SIZE,
            DEFAULT_TARGET_PERCENT_OF_HEAP));
    }

    /**
     * Creates a new type locator that creates {@link TypePool}s but provides a custom {@link TypePool.CacheProvider}.
     *
     * @param readerMode   The reader mode to use for parsing a class file.
     * @param maxCacheSize Determines how many {@link TypePool.Resolution}s can be cached at max.
     */
    public LruTypePoolCache(TypePool.Default.ReaderMode readerMode, int maxCacheSize) {
        super(readerMode);
        this.maxCacheSize = maxCacheSize;
        this.sharedCache = new AtomicReference<>(new SoftReference<>(createCache()));
        this.cacheProviders = new WeakConcurrentMap<>(false);
        this.executor = ExecutorUtils.createSingleThreadSchedulingDaemonPool("type-cache-pool-cleaner");
    }

    public static int cacheSizeForPercentageOfCommittedHeap(int minCacheSize, int maxCacheSize, double targetPercentOfHeap) {
        long maxHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted();
        int cacheSizeForTargetPercentOfHeap = (int) (maxHeap * targetPercentOfHeap / AVERAGE_SIZE_OF_TYPE_RESOLUTION);
        int cacheSize = Math.max(minCacheSize, cacheSizeForTargetPercentOfHeap);
        cacheSize = Math.min(maxCacheSize, cacheSize);
        return cacheSize;
    }

    /**
     * Schedules a background job that evicts entries that haven't been accessed since {@link #DEFAULT_STALE_ENTRY_MAX_AGE_MS}.
     *
     * @return {@code this}
     */
    public LruTypePoolCache scheduleEntryEviction() {
        return scheduleEntryEviction(DEFAULT_STALE_ENTRY_MAX_AGE_MS);
    }

    /**
     * Schedules a background job that evicts entries that haven't been accessed since the provided max age.
     *
     * @param maxAgeMs the maximum age an entry may stay in the cache since it as been accessed last
     * @return {@code this}
     */
    public LruTypePoolCache scheduleEntryEviction(final long maxAgeMs) {
        executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    evictStaleEntries(maxAgeMs);
                }
            }, maxAgeMs, maxAgeMs, TimeUnit.MILLISECONDS);
        return this;
    }

    void evictStaleEntries(long maxAgeMs) {
        long deadline = System.currentTimeMillis() - maxAgeMs;
        for (Iterator<Map.Entry<String, ResolutionsByClassLoader>> iterator = getSharedCache().entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, ResolutionsByClassLoader> entry = iterator.next();
            if (entry.getValue().isExpired(deadline)) {
                iterator.remove();
            }
        }
        cacheProviders.expungeStaleEntries();
    }

    private ConcurrentMap<String, ResolutionsByClassLoader> createCache() {
        return new ConcurrentLinkedHashMap.Builder<String, ResolutionsByClassLoader>()
            .maximumWeightedCapacity(maxCacheSize)
            .weigher(new EntryWeigher<String, ResolutionsByClassLoader>() {
                @Override
                public int weightOf(String key, ResolutionsByClassLoader value) {
                    // the key of the ConcurrentLinkedHashMap is the class name
                    // the value holds the resolutions by class loader
                    // if a given class name is loaded by more than one class loader,
                    // we need to adjust the weight of that entry accordingly
                    return Math.max(1, value.size());
                }
            })
            .build();
    }

    @Override
    protected TypePool.CacheProvider locate(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = getBootstrapMarkerLoader();
        }
        TypePool.CacheProvider cacheProvider = cacheProviders.get(classLoader);
        if (cacheProvider == null) {
            cacheProvider = new GlobalCacheProviderAdapter(new WeakReference<>(classLoader), this);
            TypePool.CacheProvider racy = cacheProviders.putIfAbsent(classLoader, cacheProvider);
            if (racy != null) {
                cacheProvider = racy;
            }
        }
        return cacheProvider;
    }

    /**
     * Copied from {@code net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy.WithTypePoolCache.Simple#getBootstrapMarkerLoader()}
     * <p>
     * Returns the class loader to serve as a cache key if a cache provider for the bootstrap class loader is requested.
     * This class loader is represented by {@code null} in the JVM which is an invalid value for many {@link ConcurrentMap}
     * implementations.
     * </p>
     * <p>
     * By default, {@link ClassLoader#getSystemClassLoader()} is used as such a key as any resource location for the
     * bootstrap class loader is performed via the system class loader within Byte Buddy as {@code null} cannot be queried
     * for resources via method calls such that this does not make a difference.
     * </p>
     *
     * @return A class loader to represent the bootstrap class loader.
     */
    private ClassLoader getBootstrapMarkerLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * Atomically gets or initializes the cache.
     * <p>
     * Inspired by {@link TypePool.CacheProvider.Simple.UsingSoftReference#register(java.lang.String, net.bytebuddy.pool.TypePool.Resolution)}
     * </p>
     */
    public ConcurrentMap<String, ResolutionsByClassLoader> getSharedCache() {
        SoftReference<ConcurrentMap<String, ResolutionsByClassLoader>> reference = sharedCache.get();
        ConcurrentMap<String, ResolutionsByClassLoader> cache = reference.get();
        if (cache == null) {
            cache = createCache();
            while (!sharedCache.compareAndSet(reference, new SoftReference<ConcurrentMap<String, ResolutionsByClassLoader>>(cache))) {
                reference = sharedCache.get();
                ConcurrentMap<String, ResolutionsByClassLoader> previous = reference.get();
                if (previous != null) {
                    cache = previous;
                    break;
                }
            }
        }
        return cache;
    }

    public static class ResolutionsByClassLoader {
        private final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());
        private final WeakConcurrentMap<ClassLoader, TypePool.Resolution> typeByClassLoader;

        public ResolutionsByClassLoader() {
            typeByClassLoader = new WeakConcurrentMap<ClassLoader, TypePool.Resolution>(false,
                ClassLoaderUtils.isPersistentClassLoader(getClass().getClassLoader()),
                // we expect that most classes are loaded by just one class loader
                new ConcurrentHashMap<AbstractWeakConcurrentMap.WeakKey<ClassLoader>, TypePool.Resolution>(1)
            );
        }

        @Nullable
        public TypePool.Resolution getResolution(ClassLoader classLoader) {
            lastAccess.set(System.currentTimeMillis());
            return typeByClassLoader.get(classLoader);
        }

        @Nullable
        public TypePool.Resolution addResolution(ClassLoader classLoader, TypePool.Resolution resolution) {
            lastAccess.set(System.currentTimeMillis());
            return typeByClassLoader.putIfAbsent(classLoader, resolution);
        }

        public int size() {
            return typeByClassLoader.approximateSize();
        }

        public boolean isExpired(long deadline) {
            return lastAccess.get() < deadline;
        }
    }

    private static class GlobalCacheProviderAdapter implements TypePool.CacheProvider {

        private final WeakReference<ClassLoader> classLoader;
        private final LruTypePoolCache cache;

        public GlobalCacheProviderAdapter(WeakReference<ClassLoader> classLoader, LruTypePoolCache cache) {
            this.classLoader = classLoader;
            this.cache = cache;
        }

        @Nullable
        @Override
        public TypePool.Resolution find(String name) {
            ClassLoader key = classLoader.get();
            ResolutionsByClassLoader resolutionByClassLoader = cache.getSharedCache().get(name);
            if (key == null || resolutionByClassLoader == null) {
                return null;
            }
            return resolutionByClassLoader.getResolution(key);
        }

        @Override
        public TypePool.Resolution register(String name, TypePool.Resolution resolution) {
            ClassLoader classLoader = this.classLoader.get();
            if (classLoader == null) {
                return resolution;
            }
            ConcurrentMap<String, ResolutionsByClassLoader> cache = this.cache.getSharedCache();
            ResolutionsByClassLoader resolutionByClassLoader = getOrCreate(name, cache);
            TypePool.Resolution racy = resolutionByClassLoader.addResolution(classLoader, resolution);
            if (racy != null) {
                resolution = racy;
            }
            // updates weight
            cache.replace(name, resolutionByClassLoader);
            return resolution;
        }

        private ResolutionsByClassLoader getOrCreate(String name, ConcurrentMap<String, ResolutionsByClassLoader> cache) {
            ResolutionsByClassLoader resolutionByClassLoader = cache.get(name);
            if (resolutionByClassLoader == null) {
                resolutionByClassLoader = new ResolutionsByClassLoader();
                ResolutionsByClassLoader racy = cache.putIfAbsent(name, resolutionByClassLoader);
                if (racy != null) {
                    resolutionByClassLoader = racy;
                }
            }
            return resolutionByClassLoader;
        }

        @Override
        public void clear() {
        }
    }
}
