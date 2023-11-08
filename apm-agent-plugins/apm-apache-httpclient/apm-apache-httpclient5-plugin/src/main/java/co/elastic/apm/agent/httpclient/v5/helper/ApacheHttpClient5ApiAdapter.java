package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.httpclient.common.ApacheHttpClientApiAdapter;
import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.StatusLine;

import java.net.URI;
import java.net.URISyntaxException;

public class ApacheHttpClient5ApiAdapter implements ApacheHttpClientApiAdapter<ClassicHttpRequest, HttpRequest, HttpHost, CloseableHttpResponse, StatusLine> {
    private static final ApacheHttpClient5ApiAdapter INSTANCE = new ApacheHttpClient5ApiAdapter();

    private ApacheHttpClient5ApiAdapter() {
    }

    public static ApacheHttpClient5ApiAdapter get() {
        return INSTANCE;
    }

    @Override
    public String getMethod(ClassicHttpRequest request) {
        return request.getMethod();
    }

    @Override
    public URI getUri(ClassicHttpRequest request) throws URISyntaxException {
        return request.getUri();
    }

    @Override
    public CharSequence getHostName(HttpHost httpHost) {
        return httpHost.getHostName();
    }

    @Override
    public int getResponseCode(CloseableHttpResponse closeableHttpResponse) {
        return closeableHttpResponse.getCode();
    }

    @Override
    public boolean isCircularRedirectException(Throwable t) {
        return t instanceof CircularRedirectException;
    }

    @Override
    public boolean isNotNullStatusLine(CloseableHttpResponse closeableHttpResponse) {
        return true;
    }
}
