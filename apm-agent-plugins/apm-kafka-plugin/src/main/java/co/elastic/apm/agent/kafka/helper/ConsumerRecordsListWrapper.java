package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

@SuppressWarnings("rawtypes")
class ConsumerRecordsListWrapper implements List<ConsumerRecord> {

    private final List<ConsumerRecord> delegate;
    private final ElasticApmTracer tracer;

    public ConsumerRecordsListWrapper(List<ConsumerRecord> delegate, ElasticApmTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
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
    public Iterator<ConsumerRecord> iterator() {
        return new ConsumerRecordsIteratorWrapper(delegate.iterator(), tracer);
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
    public boolean add(ConsumerRecord consumerRecord) {
        return delegate.add(consumerRecord);
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
    public boolean addAll(Collection<? extends ConsumerRecord> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ConsumerRecord> c) {
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
    public ConsumerRecord get(int index) {
        return delegate.get(index);
    }

    @Override
    public ConsumerRecord set(int index, ConsumerRecord element) {
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, ConsumerRecord element) {
        delegate.add(index, element);
    }

    @Override
    public ConsumerRecord remove(int index) {
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
    public ListIterator<ConsumerRecord> listIterator() {
        return delegate.listIterator();
    }

    @Override
    public ListIterator<ConsumerRecord> listIterator(int index) {
        return delegate.listIterator(index);
    }

    @Override
    public List<ConsumerRecord> subList(int fromIndex, int toIndex) {
        return new ConsumerRecordsListWrapper(delegate.subList(fromIndex, toIndex), tracer);
    }
}
