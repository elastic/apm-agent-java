package co.elastic.apm.objectpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectPoolTest {

    private ObjectPool<TestRecyclable> objectPool;

    @BeforeEach
    void setUp() {
        objectPool = new ObjectPool<>(10, false, TestRecyclable::new);
    }

    @Test
    public void testMaxElements() throws Exception {
        for (int i = 0; i < 20; i++) {
            objectPool.recycle(new TestRecyclable(i));
        }
        assertThat(objectPool.getCurrentThreadsQueueSize()).isEqualTo(10);
    }

    @Test
    public void testDifferentThreads_DifferentQueues() throws Exception {
        objectPool.recycle(new TestRecyclable());
        assertThat(objectPool.getCurrentThreadsQueueSize()).isEqualTo(1);

        final Thread t1 = new Thread(() -> {
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            assertThat(objectPool.getCurrentThreadsQueueSize()).isEqualTo(2);
        });
        t1.start();
        t1.join();

        final Thread t2 = new Thread(() -> {
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            assertThat(objectPool.getCurrentThreadsQueueSize()).isEqualTo(3);
        });
        t2.start();
        t2.join();
    }

    @Test
    public void testRecycle() throws Exception {
        final TestRecyclable instance = objectPool.createInstance();
        instance.state = 1;
        objectPool.recycle(instance);
        assertThat(instance.state).isEqualTo(0);
        assertThat(instance).isSameAs(objectPool.createInstance());
    }

    private static class TestRecyclable implements Recyclable {

        private int state;

        TestRecyclable() {
        }

        TestRecyclable(int state) {
            this.state = state;
        }

        @Override
        public void resetState() {
            state = 0;
        }
    }
}
