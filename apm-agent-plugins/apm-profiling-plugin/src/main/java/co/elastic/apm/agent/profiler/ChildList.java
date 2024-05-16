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
package co.elastic.apm.agent.profiler;


import co.elastic.apm.agent.sdk.internal.collections.LongList;

/** List for maintaining pairs of (spanId,parentIds) both represented as longs. */
public class ChildList {

    // this list contains the (spanId,parentIds) flattened
    private final LongList idsWithParentIds = new LongList();

    public void add(long id, long parentId) {
        idsWithParentIds.add(id);
        idsWithParentIds.add(parentId);
    }

    public long getId(int index) {
        return idsWithParentIds.get(index * 2);
    }

    public long getParentId(int index) {
        return idsWithParentIds.get(index * 2 + 1);
    }

    public int getSize() {
        return idsWithParentIds.getSize() / 2;
    }

    public void addAll(ChildList other) {
        idsWithParentIds.addAll(other.idsWithParentIds);
    }

    public void clear() {
        idsWithParentIds.clear();
    }

    public boolean isEmpty() {
        return getSize() == 0;
    }

    public void removeLast() {
        int size = idsWithParentIds.getSize();
        idsWithParentIds.remove(size - 1);
        idsWithParentIds.remove(size - 2);
    }
}
