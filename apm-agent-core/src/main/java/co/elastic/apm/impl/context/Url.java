
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * A complete Url, with scheme, host and path.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "raw",
    "protocol",
    "full",
    "hostname",
    "port",
    "pathname",
    "search",
    "hash"
})
public class Url implements Recyclable {

    /**
     * The raw, unparsed URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("raw")
    @JsonPropertyDescription("The raw, unparsed URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.")
    private String raw;
    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @JsonProperty("protocol")
    @JsonPropertyDescription("The protocol of the request, e.g. 'https:'.")
    private String protocol;
    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    @JsonPropertyDescription("The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.")
    private String full;
    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @JsonProperty("hostname")
    @JsonPropertyDescription("The hostname of the request, e.g. 'example.com'.")
    private String hostname;
    /**
     * The port of the request, e.g. '443'
     */
    @JsonProperty("port")
    @JsonPropertyDescription("The port of the request, e.g. '443'")
    private String port;
    /**
     * The path of the request, e.g. '/search'
     */
    @JsonProperty("pathname")
    @JsonPropertyDescription("The path of the request, e.g. '/search'")
    private String pathname;
    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @JsonProperty("search")
    @JsonPropertyDescription("The search describes the query string of the request. It is expected to have values delimited by ampersands.")
    private String search;

    /**
     * The raw, unparsed URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("raw")
    public String getRaw() {
        return raw;
    }

    /**
     * The raw, unparsed URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("raw")
    public void setRaw(String raw) {
        this.raw = raw;
    }

    public Url withRaw(String raw) {
        this.raw = raw;
        return this;
    }

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @JsonProperty("protocol")
    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @JsonProperty("protocol")
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Url withProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    public String getFull() {
        return full;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    public void setFull(String full) {
        this.full = full;
    }

    public Url withFull(String full) {
        this.full = full;
        return this;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @JsonProperty("hostname")
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Url withHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * The port of the request, e.g. '443'
     */
    @JsonProperty("port")
    public String getPort() {
        return port;
    }

    /**
     * The port of the request, e.g. '443'
     */
    @JsonProperty("port")
    public void setPort(String port) {
        this.port = port;
    }

    public Url withPort(String port) {
        this.port = port;
        return this;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @JsonProperty("pathname")
    public String getPathname() {
        return pathname;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @JsonProperty("pathname")
    public void setPathname(String pathname) {
        this.pathname = pathname;
    }

    public Url withPathname(String pathname) {
        this.pathname = pathname;
        return this;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @JsonProperty("search")
    public String getSearch() {
        return search;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @JsonProperty("search")
    public void setSearch(String search) {
        this.search = search;
    }

    public Url withSearch(String search) {
        this.search = search;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("raw", raw).append("protocol", protocol).append("full", full).append("hostname", hostname).append("port", port).append("pathname", pathname).append("search", search).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(protocol).append(hostname).append(search).append(port).append(raw).append(full).append(pathname).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Url) == false) {
            return false;
        }
        Url rhs = ((Url) other);
        return new EqualsBuilder().append(protocol, rhs.protocol).append(hostname, rhs.hostname).append(search, rhs.search).append(port, rhs.port).append(raw, rhs.raw).append(full, rhs.full).append(pathname, rhs.pathname).isEquals();
    }

    @Override
    public void resetState() {
        raw = null;
        protocol = null;
        full = null;
        hostname = null;
        port = null;
        pathname = null;
        search = null;
    }
}
