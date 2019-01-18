package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.ContextInScopeRunnableWrapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

class FailingExecutorInstrumentationTest extends AbstractInstrumentationTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = ExecutorServiceWrapper.wrap(new ForkJoinPool() {
            @Override
            public ForkJoinTask<?> submit(Runnable task) {
                if (task instanceof ContextInScopeRunnableWrapper) {
                    throw new IllegalArgumentException();
                }
                return super.submit(task);
            }

            @Override
            public void execute(Runnable task) {
                throw new UnsupportedOperationException();
            }
        });
        tracer.startTransaction().activate();
    }

    @AfterEach
    void tearDown() {
        tracer.currentTransaction().deactivate().end();
    }


    @Test
    @Disabled
    void testWrappersNotSupported() throws Exception {
        executor.submit(() -> {
        }).get();
    }

    @Test
    void testUnsupported() {
        Assertions.assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(UnsupportedOperationException.class);
    }
}
