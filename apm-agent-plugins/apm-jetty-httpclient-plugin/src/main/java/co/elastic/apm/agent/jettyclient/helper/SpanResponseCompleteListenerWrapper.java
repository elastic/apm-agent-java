package co.elastic.apm.agent.jettyclient.helper;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;

public class SpanResponseCompleteListenerWrapper implements Response.CompleteListener {

    private final Span span;

    public SpanResponseCompleteListenerWrapper(Span span) {
        this.span = span;
    }

    @Override
    public void onComplete(Result result) {
        if (span != null) {
            System.out.println("### onComplete");
            try {
                Response response = result.getResponse();
                Throwable t = result.getFailure();
                if (response != null) {
                    span.getContext().getHttp().withStatusCode(response.getStatus());
                } else if (t != null) {
                    span.withOutcome(Outcome.FAILURE);
                }
                span.captureException(t);
            } finally {
                span.deactivate().end();
            }
        }
    }
}
