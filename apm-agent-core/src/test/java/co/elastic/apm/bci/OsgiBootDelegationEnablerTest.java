package co.elastic.apm.bci;

import co.elastic.apm.impl.ElasticApmTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OsgiBootDelegationEnablerTest {

    private final OsgiBootDelegationEnabler osgiBootDelegationEnabler = new OsgiBootDelegationEnabler();

    @BeforeEach
    @AfterEach
    void clearState() {
        System.clearProperty("org.osgi.framework.bootdelegation");
        System.clearProperty("atlassian.org.osgi.framework.bootdelegation");
    }

    @Test
    void testBootdelegation() {
        osgiBootDelegationEnabler.start(mock(ElasticApmTracer.class));
        assertThat(System.getProperties())
            .containsEntry("org.osgi.framework.bootdelegation", "co.elastic.apm.*")
            .containsEntry("atlassian.org.osgi.framework.bootdelegation", "co.elastic.apm.*");
    }

    @Test
    void testBootdelegationWithExistingProperty() {
        System.setProperty("org.osgi.framework.bootdelegation", "foo.bar");
        osgiBootDelegationEnabler.start(mock(ElasticApmTracer.class));
        assertThat(System.getProperties())
            .containsEntry("org.osgi.framework.bootdelegation", "foo.bar,co.elastic.apm.*")
            .containsEntry("atlassian.org.osgi.framework.bootdelegation", "co.elastic.apm.*");
    }
}
