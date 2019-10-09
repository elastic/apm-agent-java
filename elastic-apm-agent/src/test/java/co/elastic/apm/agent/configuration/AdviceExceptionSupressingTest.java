package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdviceExceptionSupressingTest {

    @Test
    void testAllAdvicesSuppressExceptions() {
        List<ElasticApmInstrumentation> instrumentations = DependencyInjectingServiceLoader.load(ElasticApmInstrumentation.class, MockTracer.create());
        for (ElasticApmInstrumentation instrumentation : instrumentations) {
            for (Method method : instrumentation.getClass().getDeclaredMethods()) {
                Advice.OnMethodEnter onMethodEnter = method.getAnnotation(Advice.OnMethodEnter.class);
                if (onMethodEnter != null) {
                    assertThat(onMethodEnter.suppress()).isEqualTo(Throwable.class);
                }
                Advice.OnMethodExit onMethodExit = method.getAnnotation(Advice.OnMethodExit.class);
                if (onMethodExit != null) {
                    assertThat(onMethodExit.suppress()).isEqualTo(Throwable.class);
                }
            }
        }
    }
}
