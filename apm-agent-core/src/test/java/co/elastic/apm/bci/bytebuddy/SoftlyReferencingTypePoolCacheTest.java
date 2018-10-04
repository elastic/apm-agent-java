package co.elastic.apm.bci.bytebuddy;

import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoftlyReferencingTypePoolCacheTest {

    private SoftlyReferencingTypePoolCache cache;

    @BeforeEach
    void setUp() {
        cache = new SoftlyReferencingTypePoolCache(TypePool.Default.ReaderMode.FAST, 42, ElementMatchers.none());
    }

    @Test
    void testClearEntries() {
        cache.locate(ClassLoader.getSystemClassLoader());
        assertThat(cache.getCacheProviders().approximateSize()).isEqualTo(1);
        cache.clearIfNotAccessedSince(0);
        assertThat(cache.getCacheProviders().approximateSize()).isEqualTo(0);
    }
}
