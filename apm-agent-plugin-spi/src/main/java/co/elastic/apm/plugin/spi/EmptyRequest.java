package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.util.Enumeration;

public class EmptyRequest implements Request {

    public static final Request INSTANCE = new EmptyRequest();

    private EmptyRequest() {
    }

    @Override
    public boolean hasContent() {
        return false;
    }

    @Override
    public Request withMethod(@Nullable String method) {
        return this;
    }

    @Override
    public Socket getSocket() {
        return EmptySocket.INSTANCE;
    }

    @Override
    public Url getUrl() {
        return EmptyUrl.INSTANCE;
    }

    @Override
    public PotentiallyMultiValuedMap getHeaders() {
        return EmptyPotentiallyMultiValuedMap.INSTANCE;
    }

    @Override
    public PotentiallyMultiValuedMap getCookies() {
        return EmptyPotentiallyMultiValuedMap.INSTANCE;
    }

    @Override
    public Request withHttpVersion(@Nullable String httpVersion) {
        return this;
    }

    @Override
    public CharBuffer withBodyBuffer() {
        return null;
    }

    @Override
    public void redactBody() {
    }

    @Override
    public Request addFormUrlEncodedParameters(String key, String[] values) {
        return this;
    }

    @Override
    public Request addHeader(String headerName, @Nullable Enumeration<String> headerValues) {
        return this;
    }

    @Override
    public Request addCookie(String cookieName, String cookieValue) {
        return this;
    }

    @Nullable
    @Override
    public CharBuffer getBodyBuffer() {
        return null;
    }

    @Override
    public void endOfBufferInput() {
    }

    @Override
    public void setRawBody(String rawBody) {
    }
}
