package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@VisibleForAdvice
public class JaxRsTransactionHelper {

    private final JaxRsConfiguration jaxRsConfiguration;

    @VisibleForAdvice
    public JaxRsTransactionHelper(ElasticApmTracer tracer) {
        this.jaxRsConfiguration = tracer.getConfig(JaxRsConfiguration.class);
    }

    @VisibleForAdvice
    public void setTransactionName(@Nonnull Transaction currentTransaction,
                                   @Nonnull String signature,
                                   @Nullable String pathAnnotationValue)  {
        currentTransaction.withName(signature);
        if (jaxRsConfiguration.isUsePathAnnotationValueForTransactionName()) {
            if (pathAnnotationValue != null && !pathAnnotationValue.isEmpty()) {
                currentTransaction.setName(pathAnnotationValue);
            }
        }
    }

}
