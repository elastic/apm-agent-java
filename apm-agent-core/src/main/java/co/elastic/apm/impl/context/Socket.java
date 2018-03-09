
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Socket implements Recyclable {

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
    private boolean encrypted;
    @JsonProperty("remote_address")
    private String remoteAddress;

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
    public boolean isEncrypted() {
        return encrypted;
    }

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public Socket withEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
        return this;
    }

    @JsonProperty("remote_address")
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @JsonProperty("remote_address")
    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public Socket withRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("encrypted", encrypted).append("remoteAddress", remoteAddress).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(encrypted).append(remoteAddress).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Socket) == false) {
            return false;
        }
        Socket rhs = ((Socket) other);
        return new EqualsBuilder().append(encrypted, rhs.encrypted).append(remoteAddress, rhs.remoteAddress).isEquals();
    }

    @Override
    public void resetState() {
        encrypted = false;
        remoteAddress = null;
    }

    public void copyFrom(Socket other) {
        this.encrypted = other.encrypted;
        this.remoteAddress = other.remoteAddress;
    }
}
