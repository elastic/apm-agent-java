package co.elastic.apm.agent.collections;

import java.util.Arrays;

public class LongList {
    private static final int DEFAULT_CAPACITY = 16;
    private long[] longs;
    private int size;

    public LongList() {
        this(DEFAULT_CAPACITY);
    }

    public LongList(int initialCapacity) {
        longs = new long[initialCapacity];
    }

    public void add(long l) {
        ensureCapacity(size + 1);
        longs[size++] = l;
    }

    public void addAll(LongList other) {
        ensureCapacity(size + other.size);
        for (int i = 0; i < other.size; i++) {
            longs[size++] = other.longs[i];
        }
    }

    private void ensureCapacity(int size) {
        if (longs.length < size) {
            longs = Arrays.copyOf(longs, longs.length * 2);
        }
    }

    public int getSize() {
        return size;
    }

    public long get(int i) {
        if (i >= size) {
            throw new IndexOutOfBoundsException();
        }
        return longs[i];
    }

    public boolean contains(long l) {
        for (int i = 0; i < size; i++) {
            if (longs[i] == l) {
                return true;
            }
        }
        return false;
    }
}
