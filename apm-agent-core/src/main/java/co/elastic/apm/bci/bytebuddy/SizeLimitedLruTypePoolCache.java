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

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weigher;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

import java.util.concurrent.ConcurrentMap;

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

    private final WeakConcurrentMap<ClassLoader, TypePool.CacheProvider> cacheProviders = new WeakConcurrentMap
        .WithInlinedExpunction<ClassLoader, TypePool.CacheProvider>();
    private final long maxCacheSizeBytes;

    public SizeLimitedLruTypePoolCache(long maxCacheSizeBytes, TypePool.Default.ReaderMode readerMode) {
        super(readerMode);
        this.maxCacheSizeBytes = maxCacheSizeBytes;
    }

    @Override
    protected TypePool.CacheProvider locate(ClassLoader classLoader) {
        classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
        TypePool.CacheProvider cacheProvider = cacheProviders.get(classLoader);
        while (cacheProvider == null) {
            cacheProvider = createSizeLimitedCacheProvider();
            TypePool.CacheProvider previous = cacheProviders.put(classLoader, cacheProvider);
            if (previous != null) {
                cacheProvider = previous;
            }
        }
        return cacheProvider;
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
     * Duplicates some code from {@link net.bytebuddy.pool.TypePool.CacheProvider.Simple},
     * because it does not take a {@link ConcurrentMap} as a constructor argument.
     */
    private static class SizeLimitedTypePool implements TypePool.CacheProvider {

        /**
         * A map containing all cached resolutions by their names.
         */
        private final ConcurrentMap<String, TypePool.Resolution> cache;


        private SizeLimitedTypePool(ConcurrentLinkedHashMap<String, TypePool.Resolution> cache) {
            this.cache = cache;
        }

        @Override
        public TypePool.Resolution find(String name) {
            return cache.get(name);
        }

        @Override
        public TypePool.Resolution register(String name, TypePool.Resolution resolution) {
            TypePool.Resolution cached = cache.putIfAbsent(name, resolution);
            return cached == null ? resolution : cached;
        }

        @Override
        public void clear() {
            cache.clear();
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
