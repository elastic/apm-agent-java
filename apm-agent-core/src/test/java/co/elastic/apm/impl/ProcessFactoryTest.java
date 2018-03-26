package co.elastic.apm.impl;

import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.ProcessFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ProcessFactoryTest {

    @Test
    void testProcessInformationForLegacyVm() {
        ProcessInfo proc = ProcessFactory.ForLegacyVM.INSTANCE.getProcessInformation();
        assertSoftly(softly -> {
            softly.assertThat(proc.getArgv()).isNotEmpty();
            softly.assertThat(proc.getPid()).isNotEqualTo(0);
            softly.assertThat(proc.getTitle()).contains("java");
        });
    }

    @Test
    void testProcessInformationForCurrentVm() {
        ProcessInfo proc = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        assertSoftly(softly -> {
            softly.assertThat(proc.getArgv()).isNotEmpty();
            softly.assertThat(proc.getPid()).isNotEqualTo(0);
            softly.assertThat(proc.getPpid()).isNotNull();
            softly.assertThat(proc.getTitle()).contains("java");
        });
    }
}
