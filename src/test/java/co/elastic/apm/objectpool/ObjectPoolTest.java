package co.elastic.apm.objectpool;

import co.elastic.apm.objectpool.impl.ThreadLocalObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectPoolTest {

    private ObjectPool<TestRecyclable> objectPool;

    @BeforeEach
    void setUp() {
        objectPool = new ThreadLocalObjectPool<>(10, false, TestRecyclable::new);
    }

    @Test
    public void testMaxElements() throws Exception {
        for (int i = 0; i < 20; i++) {
            objectPool.recycle(new TestRecyclable(i));
        }
        assertThat(objectPool.getObjectPoolSize()).isEqualTo(10);
    }

    @Test
    public void testDifferentThreads_DifferentQueues() throws Exception {
        objectPool.recycle(new TestRecyclable());
        assertThat(objectPool.getObjectPoolSize()).isEqualTo(1);

        final Thread t1 = new Thread(() -> {
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            assertThat(objectPool.getObjectPoolSize()).isEqualTo(2);
        });
        t1.start();
        t1.join();

        final Thread t2 = new Thread(() -> {
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            objectPool.recycle(new TestRecyclable());
            assertThat(objectPool.getObjectPoolSize()).isEqualTo(3);
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

    @Test
    public void testRecycleInDifferentThread() throws Exception {
        objectPool.recycle(new TestRecyclable());
        assertThat(objectPool.getObjectPoolSize()).isEqualTo(1);
        TestRecyclable instance = objectPool.createInstance();
        assertThat(objectPool.getObjectPoolSize()).isEqualTo(0);

        final Thread t1 = new Thread(() -> {
            objectPool.recycle(instance);
            assertThat(objectPool.getObjectPoolSize()).isEqualTo(1);
        });
        t1.start();
        t1.join();

        assertThat(objectPool.getObjectPoolSize()).isEqualTo(1);
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
