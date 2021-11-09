package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.VersionUtils;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class JakartaeeJaxRsTransactionNameInstrumentation extends JaxRsTransactionNameInstrumentation {
    public JakartaeeJaxRsTransactionNameInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    String pathClassName() {
        return "jakarta.ws.rs.Path";
    }

    public static class AdviceClass extends BaseAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setTransactionName(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                              @JaxRsOffsetMappingFactory.JaxRsPath @Nullable String pathAnnotationValue) {
            setTransactionName(signature, pathAnnotationValue, VersionUtils.getVersion(jakarta.ws.rs.GET.class, "jakarta.ws.rs", "jakarta.ws.rs-api"));
        }
    }
}
