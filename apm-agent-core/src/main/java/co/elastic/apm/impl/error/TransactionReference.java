
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Data for correlating errors with transactions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionReference implements Recyclable {

    /**
     * ID for the transaction
     */
    @JsonProperty("id")
    private final TransactionId id = new TransactionId();

    /**
     * UUID for the transaction
     */
    @JsonProperty("id")
    public TransactionId getId() {
        return id;
    }

    /**
     * UUID for the transaction
     */
    public TransactionReference withId(TransactionId id) {
        this.id.copyFrom(id);
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("id", id).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TransactionReference) == false) {
            return false;
        }
        TransactionReference rhs = ((TransactionReference) other);
        return new EqualsBuilder().append(id, rhs.id).isEquals();
    }

    @Override
    public void resetState() {
        id.resetState();
    }
}
