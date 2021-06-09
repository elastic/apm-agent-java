package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.springframework.amqp.core.Message;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class MessageBatchListWrapper implements List<Message> {

    private final List<Message> delegate;
    private final ElasticApmTracer tracer;
    private final SpringAmqpTransactionHelperImpl transactionHelper;

    public MessageBatchListWrapper(List<Message> delegate, ElasticApmTracer tracer, SpringAmqpTransactionHelperImpl transactionHelper) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.transactionHelper = transactionHelper;
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
    public Iterator<Message> iterator() {
        return new MessageBatchIteratorWrapper(delegate.iterator(), tracer, transactionHelper);
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
    public boolean add(Message message) {
        return delegate.add(message);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Message> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends Message> c) {
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
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public Message get(int index) {
        return delegate.get(index);
    }

    @Override
    public Message set(int index, Message element) {
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, Message element) {
        delegate.add(index, element);
    }

    @Override
    public Message remove(int index) {
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
    public ListIterator<Message> listIterator() {
        return delegate.listIterator();
    }

    @Override
    public ListIterator<Message> listIterator(int index) {
        return delegate.listIterator(index);
    }

    @Override
    public List<Message> subList(int fromIndex, int toIndex) {
        return new MessageBatchListWrapper(delegate.subList(fromIndex, toIndex), tracer, transactionHelper);
    }
}
