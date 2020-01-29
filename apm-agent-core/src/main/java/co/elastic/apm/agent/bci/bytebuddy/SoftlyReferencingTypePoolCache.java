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

import co.elastic.apm.agent.util.ExecutorUtils;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

import javax.annotation.Nullable;
import java.lang.ref.SoftReference;
import java.util.Map;
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
 * the {@link TypePool.CacheProvider}s are wrapped in a {@link SoftReference}
 * </p>
 */
public class SoftlyReferencingTypePoolCache extends AgentBuilder.PoolStrategy.WithTypePoolCache {

    /*
     * Weakly referencing ClassLoaders to avoid class loader leaks
     * Softly referencing the type pool cache so that it does not cause OOMEs
     */
    private final WeakConcurrentMap<ClassLoader, CacheProviderWrapper> cacheProviders =
        new WeakConcurrentMap<ClassLoader, CacheProviderWrapper>(false);
    private final ElementMatcher<ClassLoader> ignoredClassLoaders;

    public SoftlyReferencingTypePoolCache(final TypePool.Default.ReaderMode readerMode,
                                          final int clearIfNotAccessedSinceMinutes, ElementMatcher.Junction<ClassLoader> ignoredClassLoaders) {
        super(readerMode);
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
        classLoader = classLoader == null ? getBootstrapMarkerLoader() : classLoader;
        CacheProviderWrapper cacheProviderRef = cacheProviders.get(classLoader);
        if (cacheProviderRef == null || cacheProviderRef.get() == null) {
            cacheProviderRef = new CacheProviderWrapper();
            cacheProviders.put(classLoader, cacheProviderRef);
            // accommodate for race condition
            cacheProviderRef = cacheProviders.get(classLoader);
        }
        final TypePool.CacheProvider cacheProvider = cacheProviderRef.get();
        // guard against edge case when the soft reference has already been cleared since evaluating the loop condition
        return cacheProvider != null ? cacheProvider : TypePool.CacheProvider.Simple.withObjectType();
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
     * <p>
     * Two exceptions of that norm are (re-)deploying a web application at runtime and dynamically loading of classes,
     * which cause interactions after the initial startup.
     * </p>
     *
     * @param clearIfNotAccessedSinceMinutes the time in minutes after which the cache should be cleared
     */
    void clearIfNotAccessedSince(long clearIfNotAccessedSinceMinutes) {
        for (Map.Entry<ClassLoader, CacheProviderWrapper> entry : cacheProviders) {
            if (System.currentTimeMillis() >= entry.getValue().getLastAccess() + TimeUnit.MINUTES.toMillis(clearIfNotAccessedSinceMinutes)) {
                cacheProviders.remove(entry.getKey());
            }
        }
    }

    WeakConcurrentMap<ClassLoader, CacheProviderWrapper> getCacheProviders() {
        return cacheProviders;
    }

    private static class CacheProviderWrapper {
        private final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());
        private final SoftReference<TypePool.CacheProvider> delegate;

        private CacheProviderWrapper() {
            this.delegate = new SoftReference<TypePool.CacheProvider>(new TypePool.CacheProvider.Simple());
        }

        long getLastAccess() {
            return lastAccess.get();
        }

        @Nullable
        TypePool.CacheProvider get() {
            return delegate.get();
        }
    }

    /**
     * Copied from {@link Simple#getBootstrapMarkerLoader()}
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
}
