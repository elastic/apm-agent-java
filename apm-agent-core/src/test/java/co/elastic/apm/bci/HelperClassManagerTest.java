package co.elastic.apm.bci;

import co.elastic.apm.impl.ElasticApmTracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HelperClassManagerTest {

    @Test
    void testLoadHelperClass() {
        final HelperClassManager<HelperClassInterface> helperClassManager = HelperClassManager.ForSingleClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.bci.HelperClassManagerTest$HelperClassImpl",
            "co.elastic.apm.bci.HelperClassManagerTest$AdditionalHelper");

        final HelperClassInterface helper = helperClassManager.getForClassLoaderOfClass(HelperClassManagerTest.class);
        assertThat(helper.helpMe()).isTrue();
        assertThat(helper.getClass().getClassLoader().getParent()).isEqualTo(HelperClassManagerTest.class.getClassLoader());
    }

    public interface HelperClassInterface {
        boolean helpMe();
    }

    public static class HelperClassImpl implements HelperClassInterface {

        @Override
        public boolean helpMe() {
            return new AdditionalHelper().helpMe2();
        }
    }

    public static class AdditionalHelper {

        public boolean helpMe2() {
            return true;
        }
    }
}
