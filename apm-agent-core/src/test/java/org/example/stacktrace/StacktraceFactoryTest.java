/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.example.stacktrace;

import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * This class intentionally is not inside the co.elastic.apm package. This is to test the {@link Stacktrace#isLibraryFrame()} feature.
 */
class StacktraceFactoryTest {

    private StacktraceConfiguration stacktraceConfiguration;
    private StacktraceFactory.CurrentThreadStackTraceFactory stacktraceFactory;

    @BeforeEach
    void setUp() {
        stacktraceConfiguration = spy(new StacktraceConfiguration());
        stacktraceFactory = new StacktraceFactory.CurrentThreadStackTraceFactory(stacktraceConfiguration);
    }

    @Test
    void fillStackTrace() {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        assertThat(stacktrace).isNotEmpty();
        assertThat(stacktrace.get(0).getAbsPath()).doesNotStartWith("co.elastic");
        assertThat(stacktrace.get(0).getFunction()).isNotEqualTo("getStackTrace");
        assertThat(stacktrace.stream().filter(st -> st.getAbsPath().endsWith("StacktraceFactoryTest"))).isNotEmpty();
        assertThat(stacktrace.stream().filter(st -> st.getAbsPath().endsWith("StacktraceFactory"))).isEmpty();
    }

    @Test
    void testAppFrame() {
        when(stacktraceConfiguration.getApplicationPackages()).thenReturn(Collections.singletonList("org.example.stacktrace"));
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        Optional<Stacktrace> thisMethodsFrame = stacktrace.stream().filter(st -> st.getAbsPath().startsWith(getClass().getName())).findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().isLibraryFrame()).isFalse();
    }

    @Test
    void testNoAppFrame() {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        Optional<Stacktrace> thisMethodsFrame = stacktrace.stream().filter(st -> st.getAbsPath().startsWith(getClass().getName())).findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().isLibraryFrame()).isTrue();
    }

    @Test
    void testFileNamePresent() {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        assertThat(stacktrace.stream().filter(st -> st.getFilename() == null)).isEmpty();
    }

    @Test
    void testNoInternalStackFrames() {
        List<Stacktrace> stacktrace = new ArrayList<>();
        stacktraceFactory.fillStackTrace(stacktrace);
        assertSoftly(softly -> {
            softly.assertThat(stacktrace.stream().filter(st -> st.getAbsPath().contains("java.lang.reflect."))).isEmpty();
            softly.assertThat(stacktrace.stream().filter(st -> st.getAbsPath().contains("sun."))).isEmpty();
        });
    }

}
