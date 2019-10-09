package co.elastic.apm.agent.util;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// TODO docs
public class CallDepth {
    private static final ThreadLocal<Map<Class<?>, AtomicInteger>> callDepthPerThread = new ThreadLocal<Map<Class<?>, AtomicInteger>>();

    public static int getAndIncrement(Class<?> clazz) {
        Map<Class<?>, AtomicInteger> callDepthForCurrentThread = callDepthPerThread.get();
        if (callDepthForCurrentThread == null) {
            callDepthForCurrentThread = new WeakHashMap<Class<?>, AtomicInteger>();
            callDepthPerThread.set(callDepthForCurrentThread);
        }
        AtomicInteger depth = callDepthForCurrentThread.get(clazz);
        if (depth == null) {
            depth = new AtomicInteger();
            callDepthForCurrentThread.put(clazz, depth);
        }
        return depth.getAndIncrement();
    }

    public static void decrement(Class<?> clazz) {
        callDepthPerThread.get().get(clazz).decrementAndGet();
        assert callDepthPerThread.get().get(clazz).get() >= 0;
    }
}
