package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
                                   @Nullable Object thiz)  {
        currentTransaction.withName(signature);
        if (jaxRsConfiguration.isUsePathAnnotationValueForTransactionName()) {
            if (thiz != null) {
                Class thizClass = thiz.getClass();
                if (thizClass != null) {
                    Annotation pathAnnotation = thizClass.getAnnotation(javax.ws.rs.Path.class);
                    if (pathAnnotation != null) {
                        Class<? extends Annotation> type = pathAnnotation.annotationType();
                        if (type != null) {
                            for (Method method : type.getDeclaredMethods()) {
                                if ("value".equals(method.getName())) {
                                    Object value = null;
                                    try {
                                        value = method.invoke(pathAnnotation, (Object[]) null);
                                    } catch (IllegalAccessException | InvocationTargetException e) {

                                    }
                                    if (value != null) {
                                        currentTransaction.setName(value.toString());
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
