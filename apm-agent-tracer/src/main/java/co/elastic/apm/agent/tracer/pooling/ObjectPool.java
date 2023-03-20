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
package co.elastic.apm.agent.tracer.pooling;

/**
 * Object pool
 *
 * @param <T> pooled object type. Does not have to implement {@link Recyclable} in order to allow for dealing with objects
 *            that are outside of elastic apm agent (like standard JDK or third party library classes).
 */
public interface ObjectPool<T> {

    /**
     * Tries to reuse any existing instance if pool has any, otherwise creates a new un-pooled instance
     *
     * @return object instance, either from pool or freshly allocated
     */
    T createInstance();

    /**
     * Recycles an object
     *
     * @param obj object to recycle
     */
    void recycle(T obj);

    void clear();
}
