
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Socket implements Recyclable {

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
    private boolean encrypted;
    @JsonProperty("remote_address")
    @Nullable
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
    public Socket withEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
        return this;
    }

    @Nullable
    @JsonProperty("remote_address")
    public String getRemoteAddress() {
        return remoteAddress;
    }

    public Socket withRemoteAddress(@Nullable String remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
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
