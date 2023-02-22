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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.List;

public interface PotentiallyMultiValuedMap extends Recyclable {
    void add(String key, String value);

    void set(String key, String... values);

    @Nullable
    String getFirst(String key);

    @Nullable
    Object get(String key);

    List<String> getAll(String key);

    boolean isEmpty();

    String getKey(int i);

    Object getValue(int i);

    int size();

    void removeIgnoreCase(String key);

    void set(int index, String value);

    boolean containsIgnoreCase(String key);
}
