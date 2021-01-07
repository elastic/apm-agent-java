package co.elastic.apm.agent.sdk.weakmap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NullSafeWeakConcurrentSetTest {

    private WeakConcurrentSet<String> set;

    @BeforeEach
    void init() {
        set = new NullSafeWeakConcurrentSet<>(WeakConcurrentSet.Cleaner.MANUAL);
    }

    @Test
    void nullValuesShouldNotThrow() {
        assertThat(set.add(null)).isFalse();
        assertThat(set.remove(null)).isFalse();
        assertThat(set.contains(null)).isFalse();
    }
}
