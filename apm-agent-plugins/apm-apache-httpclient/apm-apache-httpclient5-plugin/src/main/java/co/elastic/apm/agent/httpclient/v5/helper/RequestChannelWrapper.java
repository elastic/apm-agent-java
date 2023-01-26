package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.LoggerUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;

public class RequestChannelWrapper implements RequestChannel, Recyclable {
    private static final Logger oneTimeNoDestinationInfoLogger;

    static {
        oneTimeNoDestinationInfoLogger = LoggerUtils.logOnce(LoggerFactory.getLogger("Apache-HttpClient-5-Destination"));
    }
    private final ApacheHttpAsyncClientHelper asyncClientHelper;

    private volatile RequestChannel delegate;

    @Nullable
    private AbstractSpan<?> parent;

    @Nullable
    private Span span;

    public RequestChannelWrapper(ApacheHttpAsyncClientHelper asyncClientHelper) {
        this.asyncClientHelper = asyncClientHelper;
    }

    public RequestChannelWrapper with(RequestChannel delegate, @Nullable Span span,
                                      @Nullable AbstractSpan<?> parent) {
        this.span = span;
        if (parent != null) {
            parent.incrementReferences();
            this.parent = parent;
        }
        this.delegate = delegate;
        return this;
    }

    @Override
    public void resetState() {
        span = null;
        if (parent != null) {
            parent.decrementReferences();
            parent = null;
        }
        delegate = null;
    }

    @Override
    public void sendRequest(HttpRequest httpRequest, EntityDetails entityDetails, HttpContext httpContext) throws HttpException, IOException {
        if (httpRequest != null) {
            if (span != null) {
                String host = null;
                String protocol = null;
                int port = -1;
                URI httpRequestURI = null;
                try {
                    httpRequestURI = httpRequest.getUri();
                    if (httpRequestURI != null) {
                        host = httpRequestURI.getHost();
                        port = httpRequestURI.getPort();
                        protocol = httpRequestURI.getScheme();
                    }
                } catch (Exception e) {
                    oneTimeNoDestinationInfoLogger.warn("Failed to obtain Apache HttpClient destination info, null httpRequestURI", e);
                }
                String method = httpRequest.getMethod();
                span.withName(method).appendToName(" ");
                if (host != null) {
                    span.appendToName(host);
                }
                span.getContext().getHttp().withMethod(method).withUrl(httpRequest.getRequestUri());
                HttpClientHelper.setDestinationServiceDetails(span, protocol, host, port);
            }

            if (!TraceContext.containsTraceContextTextHeaders(httpRequest, RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(httpRequest, RequestHeaderAccessor.INSTANCE);
                } else if (parent != null) {
                    parent.propagateTraceContext(httpRequest, RequestHeaderAccessor.INSTANCE);
                }
            }
        }

        if (parent != null) {
            parent.decrementReferences();
            parent = null;
        }
        delegate.sendRequest(httpRequest, entityDetails, httpContext);
    }
}
