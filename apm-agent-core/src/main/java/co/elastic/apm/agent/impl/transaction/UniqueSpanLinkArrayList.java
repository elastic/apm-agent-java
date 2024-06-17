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
package co.elastic.apm.agent.impl.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link TraceContextImpl} list that enforces uniqueness for the purpose of span links storage.
 * We cannot naturally use {@link Set} implementations because {@link TraceContextImpl#equals} is comparing own ID with other's ID. This is
 * inappropriate for span links, which we consider equal if their <b>parent IDs</b> are equal.
 * So instead, we use a limited subclass of {@link ArrayList} that maintains a parent ID cache for equality checks.
 * As a side benefit, this gives us the ability to iterate over span links based on index and avoid the related iterator allocation, which
 * we wouldn't have if using a {@link Set}.
 */
public class UniqueSpanLinkArrayList extends ArrayList<TraceContextImpl> {

    private final Set<IdImpl> parentIdSet = new HashSet<>();

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TraceContextImpl set(int index, TraceContextImpl traceContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(TraceContextImpl traceContext) {
        if (parentIdSet.add(traceContext.getParentId())) {
            return super.add(traceContext);
        }
        return false;
    }

    @Override
    public void add(int index, TraceContextImpl traceContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TraceContextImpl remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        parentIdSet.clear();
        super.clear();
    }

    @Override
    public boolean addAll(Collection<? extends TraceContextImpl> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends TraceContextImpl> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }
}
