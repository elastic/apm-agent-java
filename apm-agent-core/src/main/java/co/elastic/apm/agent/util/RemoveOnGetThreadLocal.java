package co.elastic.apm.agent.util;

import javax.annotation.Nullable;

public class RemoveOnGetThreadLocal<T> {

    private final ThreadLocal<T> threadLocal = new ThreadLocal<>();

    public void set(@Nullable T value) {
        threadLocal.set(value);
    }

    @Nullable
    public T getAndRemove() {
        T value = threadLocal.get();
        if (value != null) {
            threadLocal.remove();
        }
        return value;
    }

}
