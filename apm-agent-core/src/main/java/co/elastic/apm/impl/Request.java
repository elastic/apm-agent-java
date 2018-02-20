
package co.elastic.apm.impl;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.MultiValueMap;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;


/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "body",
    "env",
    "headers",
    "http_version",
    "method",
    "socket",
    "url",
    "cookies"
})
public class Request implements Recyclable {

    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @JsonIgnore
    private String rawBody;
    @JsonIgnore
    private final MultiValueMap<String, String> postParams = new MultiValueMap<>();

    /**
     * The env variable is a compounded of environment information passed from the webserver.
     */
    @JsonProperty("env")
    @JsonPropertyDescription("The env variable is a compounded of environment information passed from the webserver.")
    private final Env env = new Env();

    // TODO MultiValueMap
    /**
     * Should include any headers sent by the requester. Map<String, String> </String,>will be taken by headers if supplied.
     */
    @JsonProperty("headers")
    @JsonPropertyDescription("Should include any headers sent by the requester. Map<String, String> will be taken by headers if supplied.")
    private final Map<String, String> headers = new HashMap<>();
    /**
     * HTTP version.
     */
    @JsonProperty("http_version")
    @JsonPropertyDescription("HTTP version.")
    private String httpVersion;
    /**
     * HTTP method.
     * (Required)
     */
    @JsonProperty("method")
    @JsonPropertyDescription("HTTP method.")
    private String method;
    @JsonProperty("socket")
    private final Socket socket = new Socket();
    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    @JsonProperty("url")
    @JsonPropertyDescription("A complete Url, with scheme, host and path.")
    private final Url url = new Url();
    /**
     * A parsed key-value object of cookies
     */
    @JsonProperty("cookies")
    @JsonPropertyDescription("A parsed key-value object of cookies")
    private final Map<String, String> cookies = new HashMap<>();

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

    public Request withFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    public Request withFormUrlEncodedParameters(String key, String[] values) {
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
     * The env variable is a compounded of environment information passed from the webserver.
     */
    @JsonProperty("env")
    public Env getEnv() {
        return env;
    }

    /**
     * Should include any headers sent by the requester. Map<String, String> </String,>will be taken by headers if supplied.
     */
    @JsonProperty("headers")
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * HTTP version.
     */
    @JsonProperty("http_version")
    public String getHttpVersion() {
        return httpVersion;
    }

    public Request withHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    /**
     * HTTP method.
     * (Required)
     */
    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    public Request withMethod(String method) {
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

    /**
     * A parsed key-value object of cookies
     */
    @JsonProperty("cookies")
    public Map<String, String> getCookies() {
        return cookies;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("body", getBody()).append("env", env).append("headers", headers).append("httpVersion", httpVersion).append("method", method).append("socket", socket).append("url", url).append("cookies", cookies).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(headers).append(httpVersion).append(method).append(socket).append(getBody()).append(env).append(url).append(cookies).toHashCode();
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
        return new EqualsBuilder().append(headers, rhs.headers).append(httpVersion, rhs.httpVersion).append(method, rhs.method).append(socket, rhs.socket).append(getBody(), rhs.getBody()).append(env, rhs.env).append(url, rhs.url).append(cookies, rhs.cookies).isEquals();
    }

    @Override
    public void resetState() {
        rawBody = null;
        postParams.clear();
        // TODO env;
        headers.clear();
        httpVersion = null;
        method = null;
        socket.resetState();
        url.resetState();
        cookies.clear();
    }
}
