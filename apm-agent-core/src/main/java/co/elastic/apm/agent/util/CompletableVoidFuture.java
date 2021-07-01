package co.elastic.apm.agent.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableVoidFuture implements Future<Void> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile boolean cancelled = false;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        cancelled = true;
        return true;
    }

    public void complete() {
        latch.countDown();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return latch.getCount() == 0;
    }

    @Override
    public Void get() throws InterruptedException {
        latch.await();
        return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
        return null;
    }
}
