package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@VisibleForAdvice
public class JaxRsTransactionHelper {

    private final CoreConfiguration coreConfiguration;

    @VisibleForAdvice
    public JaxRsTransactionHelper(ElasticApmTracer tracer) {
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @VisibleForAdvice
    public void setTransactionName(@Nonnull Transaction currentTransaction,
                                   @Nonnull String signature,
                                   @Nullable String pathAnnotationValue)  {
        currentTransaction.withName(signature);
        if (coreConfiguration.isUseAnnotationValueForTransactionName()) {
            if (pathAnnotationValue != null && !pathAnnotationValue.isEmpty()) {
                currentTransaction.setName(pathAnnotationValue);
            }
        }
    }

}
