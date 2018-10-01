/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci.bytebuddy;

import co.elastic.apm.util.ExecutorUtils;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weigher;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches {@link TypeDescription}s which speeds up type matching -
 * especially when the matching requires lookup of other {@link TypeDescription}s.
 * Such as when in order to match a type, it's superclass has to be determined.
 * Without a type pool cache those types would have to be re-loaded from the file system if their {@link Class} has not been loaded yet.
 * <p>
 * In order to avoid {@link OutOfMemoryError}s because of this cache,
 * a maximum size in bytes can be configured.
 * If the cache size is exceeded, the least frequently used element will be evicted.
 * Note that the cache size calculation is only a rough estimation (see {@link ResolutionWeigher}).
 * </p>
 */
public class SizeLimitedLruTypePoolCache extends AgentBuilder.PoolStrategy.WithTypePoolCache {

    /*
     * Weakly referencing ClassLoaders to avoid class loader leaks
     * Softly referencing the type pool cache so that it does not cause OOMEs
     */
    private final WeakConcurrentMap<ClassLoader, SoftReference<TypePool.CacheProvider>> cacheProviders =
        new WeakConcurrentMap<ClassLoader, SoftReference<TypePool.CacheProvider>>(false);
    private final long maxCacheSizeBytes;
    private final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());
    private final ElementMatcher<ClassLoader> ignoredClassLoaders;

    public SizeLimitedLruTypePoolCache(final long maxCacheSizeBytes, final TypePool.Default.ReaderMode readerMode,
                                       final int clearIfNotAccessedSinceMinutes, ElementMatcher.Junction<ClassLoader> ignoredClassLoaders) {
        super(readerMode);
        this.maxCacheSizeBytes = maxCacheSizeBytes;
        ExecutorUtils.createSingleThreadSchedulingDeamonPool("type-cache-pool-cleaner", 1)
            .scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    clearIfNotAccessedSince(clearIfNotAccessedSinceMinutes);
                    cacheProviders.expungeStaleEntries();
                }
            }, 1, 1, TimeUnit.MINUTES);
        this.ignoredClassLoaders = ignoredClassLoaders;
    }

    @Override
    protected TypePool.CacheProvider locate(ClassLoader classLoader) {
        if (ignoredClassLoaders.matches(classLoader)) {
            return TypePool.CacheProvider.Simple.withObjectType();
        }
        lastAccess.set(System.currentTimeMillis());
        classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
        SoftReference<TypePool.CacheProvider> cacheProvider = cacheProviders.get(classLoader);
        while (cacheProvider == null || cacheProvider.get() == null) {
            cacheProvider = new SoftReference<>(createSizeLimitedCacheProvider());
            SoftReference<TypePool.CacheProvider> previous = cacheProviders.put(classLoader, cacheProvider);
            if (previous != null) {
                cacheProvider = previous;
            }
        }
        return cacheProvider.get();
    }

    private TypePool.CacheProvider createSizeLimitedCacheProvider() {
        TypePool.CacheProvider cacheProvider = new SizeLimitedTypePool(new ConcurrentLinkedHashMap.Builder<String, TypePool.Resolution>()
            .weigher(new ResolutionWeigher())
            .maximumWeightedCapacity(maxCacheSizeBytes)
            .build());
        cacheProvider.register(Object.class.getName(), new TypePool.Resolution.Simple(TypeDescription.OBJECT));
        return cacheProvider;
    }

    /**
     * Clears the type pool cache if it has not been accessed for the specified amount of time.
     * <p>
     * This cache is mostly useful while the application starts and warms up.
     * After a certain point, all classes are loaded and this cache is not needed anymore
     * </p>
     * <p>
     * Evicting the whole cache at once has advantages over evicting on an entry-based level:
     * A resolution never gets stale or outdated, which is the main use case for having a max age for an entry.
     * Also, this model only works when the cache is frequently accessed,
     * as most caches only evict stale entries when interacting with the cache.
     * In our scenario,
     * the cache is not accessed at all once all classes have been loaded which means it would never get cleared.
     * </p>
     *
     * @param clearIfNotAccessedSinceMinutes the time in minutes after which the cache should be cleared
     */
    private void clearIfNotAccessedSince(long clearIfNotAccessedSinceMinutes) {
        if (System.currentTimeMillis() > lastAccess.get() + TimeUnit.MINUTES.toMillis(clearIfNotAccessedSinceMinutes)) {
            cacheProviders.clear();
        }
    }

    /**
     * Duplicates some code from {@link net.bytebuddy.pool.TypePool.CacheProvider.Simple},
     * because it does not take a {@link ConcurrentMap} as a constructor argument.
     */
    private static class SizeLimitedTypePool implements TypePool.CacheProvider {

        /**
         * A map containing all cached resolutions by their names.
         */
        private final ConcurrentMap<String, TypePool.Resolution> storage;

        private SizeLimitedTypePool(ConcurrentLinkedHashMap<String, TypePool.Resolution> storage) {
            this.storage = storage;
        }

        @Override
        public TypePool.Resolution find(String name) {
            return storage.get(name);
        }

        @Override
        public TypePool.Resolution register(String name, TypePool.Resolution resolution) {
            TypePool.Resolution cached = storage.putIfAbsent(name, resolution);
            return cached == null ? resolution : cached;
        }

        @Override
        public void clear() {
            storage.clear();
        }
    }

    private static class ResolutionWeigher implements Weigher<TypePool.Resolution> {
        /**
         * A quick
         * <pre>ls -al ./**{@literal /}*.class | awk '{sum += $5; n++;} END {print sum/n;}'</pre>
         * of the unzipped agent jar + dependencies yields ~3.7kb/class file.
         */
        private static final int AVG_CLASS_FILE_SIZE = 4 * 1024;

        @Override
        public int weightOf(TypePool.Resolution value) {
            // getting the actual size of the type description is difficult
            // so just returning a avg size estimate, this is probably good enough
            return AVG_CLASS_FILE_SIZE;
        }
    }
}
