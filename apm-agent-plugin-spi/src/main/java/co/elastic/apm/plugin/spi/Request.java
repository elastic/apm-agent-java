package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.util.Enumeration;

public interface Request {
    boolean hasContent();

    Request withMethod(@Nullable String method);

    Socket getSocket();

    Url getUrl();

    PotentiallyMultiValuedMap getHeaders();

    PotentiallyMultiValuedMap getCookies();

    Request withHttpVersion(@Nullable String httpVersion);

    @Nullable
    CharBuffer withBodyBuffer();

    void redactBody();

    Request addFormUrlEncodedParameters(String key, String[] values);

    Request addHeader(String headerName, @Nullable Enumeration<String> headerValues);

    Request addCookie(String cookieName, String cookieValue);

    @Nullable
    CharBuffer getBodyBuffer();

    void endOfBufferInput();

    void setRawBody(String rawBody);
}
