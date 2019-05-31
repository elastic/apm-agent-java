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

public class ElasticsearchClientSyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    public ElasticsearchClientSyncInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public Class<?> getAdviceClass() {
        return ElasticsearchRestClientSyncAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequest")
            .and(takesArguments(1)
                .and(takesArgument(0, named("org.elasticsearch.client.Request"))));
    }

    private static class ElasticsearchRestClientSyncAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(0) Request request,
                                            @Advice.Local("span") Span span,
                                            @Advice.Local("helper") ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> helper) {

            helper = esClientInstrHelperManager.getForClassLoaderOfClass(Request.class);
            if (helper != null) {
                span = helper.createClientSpan(request.getMethod(), request.getEndpoint(), request.getEntity());
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return @Nullable Response response,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Local("helper") @Nullable ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> helper,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (helper != null && span != null) {
                try {
                    helper.finishClientSpan(response, span, t);
                } finally {
                    span.deactivate();
                }
            }
        }
    }
}
