package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;

import static org.apache.commons.lang.builder.ToStringStyle.SIMPLE_STYLE;


/**
 * A complete Url, with scheme, host and path.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url implements Recyclable {

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    private final StringBuilder full = new StringBuilder();
    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    @JsonProperty("protocol")
    private String protocol;
    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    @JsonProperty("hostname")
    private String hostname;
    /**
     * The port of the request, e.g. '443'
     */
    @Nullable
    @JsonProperty("port")
    private String port;
    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    @JsonProperty("pathname")
    private String pathname;
    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    @JsonProperty("search")
    private String search;

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    @JsonProperty("protocol")
    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    public Url withProtocol(@Nullable String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    public StringBuilder getFull() {
        return full;
    }

    public Url appendToFull(CharSequence charSequence) {
        full.append(charSequence);
        return this;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    public Url withHostname(@Nullable String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * The port of the request, e.g. '443'
     */
    @Nullable
    @JsonProperty("port")
    public String getPort() {
        return port;
    }

    /**
     * The port of the request, e.g. '443'
     */
    public Url withPort(@Nullable String port) {
        this.port = port;
        return this;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    @JsonProperty("pathname")
    public String getPathname() {
        return pathname;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    public Url withPathname(@Nullable String pathname) {
        this.pathname = pathname;
        return this;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    @JsonProperty("search")
    public String getSearch() {
        return search;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    public Url withSearch(@Nullable String search) {
        this.search = search;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
            .append("protocol", protocol)
            .append("full", full)
            .append("hostname", hostname)
            .append("port", port)
            .append("pathname", pathname)
            .append("search", search).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(protocol)
            .append(hostname)
            .append(search)
            .append(port)
            .append(full.toString())
            .append(pathname).toHashCode();
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
        return new EqualsBuilder()
            .append(protocol, rhs.protocol)
            .append(hostname, rhs.hostname)
            .append(search, rhs.search)
            .append(port, rhs.port)
            .append(full.toString(), rhs.full.toString())
            .append(pathname, rhs.pathname).isEquals();
    }

    @Override
    public void resetState() {
        protocol = null;
        full.setLength(0);
        hostname = null;
        port = null;
        pathname = null;
        search = null;
    }

    public void copyFrom(Url other) {
        this.protocol = other.protocol;
        this.full.append(other.full);
        this.hostname = other.hostname;
        this.port = other.port;
        this.pathname = other.pathname;
        this.search = other.search;
    }
}
