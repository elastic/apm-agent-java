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
package co.elastic.apm.agent.util;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataStructures {

    /**
     * Use this utility method for WeakConcurrentMap creation if it is created in the context of a request processing
     * thread whose context class loader is the web application ClassLoader.
     * This leaks the web application ClassLoader when the application is undeployed/redeployed.
     * <p>
     * Tomcat will then stop the thread because it thinks it was created by the web application.
     * That means that the map will never be cleared, creating a severe memory leak.
     *
     * @param <K> map key type
     * @param <V> map value type
     * @return a new WeakConcurrentMap with a cleaner thread who's context class loader is the system/bootstrap class loader
     */
    public static <K, V> WeakConcurrentMap<K, V> createWeakConcurrentMapWithCleanerThread() {
        WeakConcurrentMap<K, V> map = new WeakConcurrentMap<>(true);
        map.getCleanerThread().setName(ThreadUtils.addElasticApmThreadPrefix(map.getCleanerThread().getName()));
        map.getCleanerThread().setContextClassLoader(null);
        return map;
    }

    public static <V> WeakConcurrentSet<V> createWeakConcurrentSetWithCleanerThread() {
        WeakConcurrentSet<V> set = new WeakConcurrentSet<>(WeakConcurrentSet.Cleaner.THREAD);
        set.getCleanerThread().setName(ThreadUtils.addElasticApmThreadPrefix(set.getCleanerThread().getName()));
        return set;
    }

}
