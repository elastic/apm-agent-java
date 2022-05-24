package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

/**
 * An instrumentation for {@link org.apache.kafka.clients.consumer.KafkaConsumer#poll} exit on new clients
 */
public class NewKafkaPollExitInstrumentation extends KafkaConsumerInstrumentation {
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers"));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.kafka.NewKafkaPollExitInstrumentation$KafkaPollExitAdvice";
    }

    public static class KafkaPollExitAdvice {

        private static final KafkaInstrumentationHeadersHelper helper = KafkaInstrumentationHeadersHelper.get();

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void pollEnd(@Advice.Thrown final Throwable throwable,
                                   @Advice.Return @Nullable ConsumerRecords<?, ?> records) {

            Span span = tracer.getActiveSpan();
            if (span != null &&
                span.getSubtype() != null && span.getSubtype().equals("kafka") &&
                span.getAction() != null && span.getAction().equals("poll")
            ) {
                helper.addSpanLinks(records, span);
                span.captureException(throwable);
                span.deactivate().end();
            }
        }
    }
}
