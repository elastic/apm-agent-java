package co.elastic.apm.agent.impl.stacktrace;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import static org.assertj.core.api.Assertions.assertThat;

class StacktraceConfigurationTest {

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanFramesMinDurationIsDefault_thenGetValueFromSpanStackTraceMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "5ms").add("span_stack_trace_min_duration", "10ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(10);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanFramesMinDurationIsZero_thenGetSwappedValueOfSpanFramesMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "0ms").add("span_stack_trace_min_duration", "10ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(-1);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanFramesMinDurationIsMinusOne_thenGetSwappedValueOfSpanFramesMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "-1ms").add("span_stack_trace_min_duration", "10ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(0);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanFramesMinDurationIsNonDefault_thenGetValueFromSpanStackTraceMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("span_frames_min_duration", "119ms").add("span_stack_trace_min_duration", "10ms"));

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(10);
    }

    @Test
    void testGetSpanStackTraceMinDurationMs_whenSpanFramesMinDurationAndStackTraceMinDurationAreDefault_thenGetDefaultValueOfSpanStackTraceMinDuration() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();

        long actual = configRegistry.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs();
        assertThat(actual).isEqualTo(5);
    }
}
