package co.elastic.apm.agent.es.restclient.v7_1;

import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ElasticsearchClientAsyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    public ElasticsearchClientAsyncInstrumentation(ElasticApmTracer tracer) { super(tracer); }

    @Override
    public Class<?> getAdviceClass() {
        return ElasticsearchRestClientAsyncAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequestAsync")
            .and(takesArguments(2)
                .and(takesArgument(0, named("org.elasticsearch.client.Request")))
                .and(takesArgument(1, named("org.elasticsearch.client.ResponseListener"))));
    }

    private static class ElasticsearchRestClientAsyncAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(0) Request request,
                                            @Advice.Argument(value = 1, readOnly = false) ResponseListener responseListener,
                                            @Advice.Local("span") Span span,
                                            @Advice.Local("wrapped") boolean wrapped,
                                            @Advice.Local("helper") ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> helper) {

            helper = esClientInstrHelperManager.getForClassLoaderOfClass(Request.class);
            if (helper != null) {
                span = helper.createClientSpan(request.getMethod(), request.getEndpoint(), request.getEntity());
                if (span != null) {
                    responseListener = helper.<ResponseListener>wrapResponseListener(responseListener, span);
                    wrapped = true;
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Argument(1) ResponseListener responseListener,
                                           @Advice.Local("span") @Nullable Span span,
                                           @Advice.Local("wrapped") boolean wrapped,
                                           @Advice.Local("helper") @Nullable ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> helper,
                                           @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                // Deactivate in this thread. Span will be ended and reported by the listener
                span.deactivate();

                if (!wrapped) {
                    // Listener is not wrapped- we need to end the span so to avoid leak and report error if occurred during method invocation
                    helper.finishClientSpan(null, span, t);
                }
            }
        }
    }
}
