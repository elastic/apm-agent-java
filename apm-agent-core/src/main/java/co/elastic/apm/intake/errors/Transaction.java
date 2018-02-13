
package co.elastic.apm.intake.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Data for correlating errors with transactions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id"
})
public class Transaction {

    /**
     * UUID for the transaction
     */
    @JsonProperty("id")
    @JsonPropertyDescription("UUID for the transaction")
    private String id;

    /**
     * UUID for the transaction
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * UUID for the transaction
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Transaction withId(String id) {
        this.id = id;
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
        if ((other instanceof Transaction) == false) {
            return false;
        }
        Transaction rhs = ((Transaction) other);
        return new EqualsBuilder().append(id, rhs.id).isEquals();
    }

}
