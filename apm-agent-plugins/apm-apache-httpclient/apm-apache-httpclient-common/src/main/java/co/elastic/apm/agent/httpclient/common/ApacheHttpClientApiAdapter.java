package co.elastic.apm.agent.httpclient.common;


import java.net.URI;
import java.net.URISyntaxException;

public interface ApacheHttpClientApiAdapter<RequestObject extends HttpRequest, HttpRequest, HttpHost, CloseableResponse, StatusLine> {
    String getMethod(RequestObject request);

    URI getUri(RequestObject request) throws URISyntaxException;

    CharSequence getHostName(HttpHost httpHost);

    int getResponseCode(CloseableResponse response);

    boolean isCircularRedirectException(Throwable t);

    StatusLine getStatusLine(CloseableResponse response);
}
