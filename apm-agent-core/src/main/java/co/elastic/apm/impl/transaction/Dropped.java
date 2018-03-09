package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dropped implements Recyclable {

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    @JsonProperty("total")
    private long total;

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    @JsonProperty("total")
    public long getTotal() {
        return total;
    }

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    public Dropped withTotal(long total) {
        this.total = total;
        return this;
    }

    /**
     * Increments the number of spans that have been dropped by the agent recording the transaction.
     */
    public Dropped increment() {
        this.total++;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("total", total).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(total).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Dropped) == false) {
            return false;
        }
        Dropped rhs = ((Dropped) other);
        return new EqualsBuilder().append(total, rhs.total).isEquals();
    }

    @Override
    public void resetState() {
        total = 0;
    }
}
