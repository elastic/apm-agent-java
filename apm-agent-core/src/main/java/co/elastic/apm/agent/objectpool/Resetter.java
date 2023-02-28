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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.tracer.pooling.Recyclable;

/**
 * Defines reset strategy to use for a given pooled object type when they are returned to pool
 *
 * @param <T> pooled object type
 */
public interface Resetter<T> {

    /**
     * Recycles a pooled object state
     *
     * @param object object to recycle
     */
    void recycle(T object);

    /**
     * Resetter for objects that implement {@link Recyclable}
     *
     * @param <T> recyclable object type
     */
    class ForRecyclable<T extends Recyclable> implements Resetter<T> {
        private static ForRecyclable INSTANCE = new ForRecyclable();

        public static <T extends Recyclable> Resetter<T> get() {
            return INSTANCE;
        }

        @Override
        public void recycle(Recyclable object) {
            object.resetState();
        }
    }

}
