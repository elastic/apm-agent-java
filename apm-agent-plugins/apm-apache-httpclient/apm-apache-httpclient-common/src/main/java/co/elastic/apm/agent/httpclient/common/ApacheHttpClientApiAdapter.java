package co.elastic.apm.agent.httpclient.common;


import java.net.URI;
import java.net.URISyntaxException;

public interface ApacheHttpClientApiAdapter<REQUEST, WRAPPER extends REQUEST, HTTPHOST, RESPONSE> {
    String getMethod(WRAPPER request);

    URI getUri(WRAPPER request) throws URISyntaxException;

    CharSequence getHostName(HTTPHOST httpHost);

    int getResponseCode(RESPONSE response);

    boolean isCircularRedirectException(Throwable t);

    boolean isNotNullStatusLine(RESPONSE response);
}
