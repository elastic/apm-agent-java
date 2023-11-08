package co.elastic.apm.agent.httpclient.v4.helper;


import co.elastic.apm.agent.httpclient.common.ApacheHttpClientApiAdapter;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.StatusLine;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;

import java.net.URI;

public class ApacheHttpClient4ApiAdapter implements ApacheHttpClientApiAdapter<HttpRequestWrapper, HttpRequest, HttpHost, CloseableHttpResponse, StatusLine> {
    private static final ApacheHttpClient4ApiAdapter INSTANCE = new ApacheHttpClient4ApiAdapter();

    private ApacheHttpClient4ApiAdapter() {
    }

    public static ApacheHttpClient4ApiAdapter get() {
        return INSTANCE;
    }

    @Override
    public String getMethod(HttpRequestWrapper request) {
        return request.getMethod();
    }

    @Override
    public URI getUri(HttpRequestWrapper request) {
        return request.getURI();
    }

    @Override
    public CharSequence getHostName(HttpHost httpHost) {
        return httpHost.getHostName();
    }

    @Override
    public int getResponseCode(CloseableHttpResponse closeableHttpResponse) {
        final StatusLine statusLine = closeableHttpResponse.getStatusLine();
        if (statusLine == null) {
            return 0;
        }
        return statusLine.getStatusCode();
    }

    @Override
    public boolean isCircularRedirectException(Throwable t) {
        return t instanceof CircularRedirectException;
    }

    @Override
    public StatusLine getStatusLine(CloseableHttpResponse o) {
        return o.getStatusLine();
    }
}
