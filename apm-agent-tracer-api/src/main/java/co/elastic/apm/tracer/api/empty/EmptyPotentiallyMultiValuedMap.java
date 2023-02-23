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
package co.elastic.apm.tracer.api.empty;

import co.elastic.apm.tracer.api.metadata.PotentiallyMultiValuedMap;

import javax.annotation.Nullable;

public class EmptyPotentiallyMultiValuedMap implements PotentiallyMultiValuedMap {

    public static final PotentiallyMultiValuedMap INSTANCE = new EmptyPotentiallyMultiValuedMap();

    private EmptyPotentiallyMultiValuedMap() {
    }

    @Override
    public void add(String key, String value) {
    }

    @Nullable
    @Override
    public String getFirst(String key) {
        return null;
    }

    @Nullable
    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean containsIgnoreCase(String key) {
        return false;
    }
}
