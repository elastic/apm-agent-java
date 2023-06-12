package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Invoked by {@link ServletApiAdapterTest#checkAncientServletApiVersionDoesNotThrowErrors()}
 */
@TestClassWithDependencyRunner.DisableOutsideOfRunner
public class AncientServletAPITest {

    @Test
    public void checkGetClassloaderNotPresent() {
        assertThatThrownBy(() -> ServletContext.class.getMethod("getClassLoader"))
            .isInstanceOf(NoSuchMethodException.class);
        ServletContext servletContext = mock(ServletContext.class);
        assertThat(JavaxServletApiAdapter.get().getClassLoader(servletContext)).isNull();
    }
}
