package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.httpclient.RequestBodyRecordingHelper;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import org.springframework.http.client.reactive.ClientHttpRequest;

import javax.annotation.Nullable;

public class BodyCaptureRegistry {

    private static final WeakMap<ClientHttpRequest, RequestBodyRecordingHelper> PENDING_RECORDINGS = WeakConcurrent.buildMap();

    public static void maybeCaptureBodyFor(AbstractSpan<?> abstractSpan, ClientHttpRequest request) {
        if (!(abstractSpan instanceof Span<?>)) {
            return;
        }
        Span<?> span = (Span<?>) abstractSpan;
        if (span.getContext().getHttp().getRequestBody().startCapture()) {
            PENDING_RECORDINGS.put(request, new RequestBodyRecordingHelper(span));
        }
    }

    @Nullable
    public static RequestBodyRecordingHelper activateRecording(ClientHttpRequest request) {
        return PENDING_RECORDINGS.remove(request);
    }
}
