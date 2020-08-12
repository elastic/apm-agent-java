package co.elastic.apm.agent.httpclient.helper;

import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nonnull;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class AsyncCallbackCreatorImpl implements AsyncCallbackCreator {

    @Override
    public BiConsumer<HttpResponse, Throwable> create(Span span) {
        return new AsyncCallback(span);
    }

    public static class AsyncCallback implements BiConsumer<HttpResponse, Throwable> {
        private final Span span;

        public AsyncCallback(@Nonnull Span span) {
            this.span = span;
        }

        @Override
        public void accept(HttpResponse response, Throwable t) {
            try {
                if (response != null) {
                    int statusCode = response.statusCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
                span.captureException(t);
            } finally {
                span.end();
            }
        }
    }
}
