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
package co.elastic.apm.agent.rocketmq.helper;

import org.apache.rocketmq.common.message.MessageExt;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

class ConsumeMessageListWrapper implements List<MessageExt> {

    private final List<MessageExt> delegate;

    private final RocketMQInstrumentationHelperImpl helper;

    ConsumeMessageListWrapper(List<MessageExt> delegate, RocketMQInstrumentationHelperImpl helper) {
        this.delegate = delegate;
        this.helper = helper;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<MessageExt> iterator() {
        return new ConsumeMessageIteratorWrapper(delegate.iterator(), helper);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(MessageExt messageExt) {
        return delegate.add(messageExt);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.remove(c);
    }

    @Override
    public boolean addAll(Collection<? extends MessageExt> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends MessageExt> c) {
        return delegate.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public MessageExt get(int index) {
        return delegate.get(index);
    }

    @Override
    public MessageExt set(int index, MessageExt element) {
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, MessageExt element) {
        delegate.add(index, element);
    }

    @Override
    public MessageExt remove(int index) {
        return delegate.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<MessageExt> listIterator() {
        return delegate.listIterator();
    }

    @Override
    public ListIterator<MessageExt> listIterator(int index) {
        return delegate.listIterator(index);
    }

    @Override
    public List<MessageExt> subList(int fromIndex, int toIndex) {
        return new ConsumeMessageListWrapper(delegate.subList(fromIndex, toIndex), helper);
    }
}
