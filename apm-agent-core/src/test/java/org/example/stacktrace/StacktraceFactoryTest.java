package org.example.stacktrace;

import co.elastic.apm.impl.Stacktrace;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class StacktraceFactoryTest {

    private static StacktraceConfiguration stacktraceConfiguration;

    static List<StacktraceFactory> stacktraceFactoryImpls() {
        stacktraceConfiguration = spy(new StacktraceConfiguration());
        return Arrays.asList(
            new StacktraceFactory.StackWalkerStackTraceFactory(stacktraceConfiguration),
            new StacktraceFactory.CurrentThreadStackTraceFactory(stacktraceConfiguration));
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(stacktraceConfiguration);
    }

    @ParameterizedTest
    @MethodSource("stacktraceFactoryImpls")
    void fillStackTrace(StacktraceFactory stacktraceFactory) {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        assertThat(stacktrace).isNotEmpty();
        assertThat(stacktrace.get(0).getAbsPath()).doesNotStartWith("co.elastic");
        assertThat(stacktrace.get(0).getFunction()).isNotEqualTo("getStackTrace");
        assertThat(stacktrace.stream().filter(st -> st.getAbsPath().endsWith("StacktraceFactoryTest"))).isNotEmpty();
        assertThat(stacktrace.stream().filter(st -> st.getAbsPath().endsWith("StacktraceFactory"))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("stacktraceFactoryImpls")
    void testAppFrame(StacktraceFactory stacktraceFactory) {
        when(stacktraceConfiguration.getApplicationPackages()).thenReturn(Collections.singletonList("org.example.stacktrace"));
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        Optional<Stacktrace> thisMethodsFrame = stacktrace.stream().filter(st -> st.getAbsPath().startsWith(getClass().getName())).findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().isLibraryFrame()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("stacktraceFactoryImpls")
    void testNoAppFrame(StacktraceFactory stacktraceFactory) {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        Optional<Stacktrace> thisMethodsFrame = stacktrace.stream().filter(st -> st.getAbsPath().startsWith(getClass().getName())).findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().isLibraryFrame()).isTrue();
    }

}
