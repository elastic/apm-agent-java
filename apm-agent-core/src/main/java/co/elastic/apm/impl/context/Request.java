package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;
import java.util.Map;


/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Request implements Recyclable {

    @JsonIgnore
    private final PotentiallyMultiValuedMap<String, String> postParams = new PotentiallyMultiValuedMap<>();
    /**
     * Should include any headers sent by the requester. Map<String, String> </String,>will be taken by headers if supplied.
     */
    @JsonProperty("headers")
    private final PotentiallyMultiValuedMap<String, String> headers = new PotentiallyMultiValuedMap<>();
    @JsonProperty("socket")
    private final Socket socket = new Socket();
    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    @JsonProperty("url")
    private final Url url = new Url();
    /**
     * A parsed key-value object of cookies
     */
    @JsonProperty("cookies")
    private final PotentiallyMultiValuedMap<String, String> cookies = new PotentiallyMultiValuedMap<>();
    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    @JsonIgnore
    private String rawBody;
    /**
     * HTTP version.
     */
    @Nullable
    @JsonProperty("http_version")
    private String httpVersion;
    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    @JsonProperty("method")
    private String method;

    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @JsonProperty("body")
    public Object getBody() {
        if (!postParams.isEmpty()) {
            return postParams;
        } else if (rawBody != null) {
            return rawBody;
        }
        return "[REDACTED]";
    }

    public Request addFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    public Request addFormUrlEncodedParameters(String key, String[] values) {
        for (String value : values) {
            this.postParams.add(key, value);
        }
        return this;
    }

    public Request withRawBody(String rawBody) {
        this.rawBody = rawBody;
        return this;
    }

    /**
     * Adds a request header.
     *
     * @param headerName  The name of the header.
     * @param headerValue The value of the header.
     * @return <code>this</code>, for fluent method chaining
     */
    public Request addHeader(String headerName, String headerValue) {
        headers.add(headerName, headerValue);
        return this;
    }

    /**
     * Should include any headers sent by the requester.
     */
    @JsonProperty("headers")
    public PotentiallyMultiValuedMap<String, String> getHeaders() {
        return headers;
    }

    /**
     * HTTP version.
     */
    @Nullable
    @JsonProperty("http_version")
    public String getHttpVersion() {
        return httpVersion;
    }

    public Request withHttpVersion(@Nullable String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    public Request withMethod(@Nullable String method) {
        this.method = method;
        return this;
    }

    @JsonProperty("socket")
    public Socket getSocket() {
        return socket;
    }

    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    @JsonProperty("url")
    public Url getUrl() {
        return url;
    }


    public Request addCookie(String cookieName, String cookieValue) {
        cookies.add(cookieName, cookieValue);
        return this;
    }

    /**
     * A parsed key-value object of cookies
     */
    @JsonProperty("cookies")
    public Map<String, Object> getCookies() {
        return cookies;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("body", getBody())
            .append("headers", headers)
            .append("httpVersion", httpVersion)
            .append("method", method)
            .append("socket", socket)
            .append("url", url)
            .append("cookies", cookies).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(headers)
            .append(httpVersion)
            .append(method)
            .append(socket)
            .append(getBody())
            .append(url)
            .append(cookies).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Request) == false) {
            return false;
        }
        Request rhs = ((Request) other);
        return new EqualsBuilder()
            .append(headers, rhs.headers)
            .append(httpVersion, rhs.httpVersion)
            .append(method, rhs.method)
            .append(socket, rhs.socket)
            .append(getBody(), rhs.getBody())
            .append(url, rhs.url)
            .append(cookies, rhs.cookies).isEquals();
    }

    @Override
    public void resetState() {
        rawBody = null;
        postParams.clear();
        headers.clear();
        httpVersion = null;
        method = null;
        socket.resetState();
        url.resetState();
        cookies.clear();
    }

    public void copyFrom(Request other) {
        this.rawBody = other.rawBody;
        this.postParams.putAll(other.postParams);
        this.headers.putAll(other.headers);
        this.httpVersion = other.httpVersion;
        this.method = other.method;
        this.socket.copyFrom(other.socket);
        this.url.copyFrom(other.url);
        this.cookies.putAll(other.cookies);
    }
}
