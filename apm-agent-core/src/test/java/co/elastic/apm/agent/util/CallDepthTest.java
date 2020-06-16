package co.elastic.apm.agent.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallDepthTest {

    private CallDepth callDepth;

    @BeforeEach
    void setUp() {
        callDepth = CallDepth.get(CallDepthTest.class);
    }

    @AfterEach
    void tearDown() {
        CallDepth.clearRegistry();
    }

    @Test
    void testDetectNestedCalls() {
        assertThat(callDepth.isNestedCallAndIncrement()).isFalse();
        assertThat(callDepth.isNestedCallAndIncrement()).isTrue();
        assertThat(callDepth.isNestedCallAndDecrement()).isTrue();
        assertThat(callDepth.isNestedCallAndDecrement()).isFalse();
    }

    @Test
    void testNegativeCount() {
        assertThatThrownBy(() -> callDepth.decrement()).isInstanceOf(AssertionError.class);
    }
}
