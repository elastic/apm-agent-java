
package co.elastic.apm.intake.transactions;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "dropped"
})
public class SpanCount implements Recyclable {

    @JsonProperty("dropped")
    private final Dropped dropped = new Dropped();

    @JsonProperty("dropped")
    public Dropped getDropped() {
        return dropped;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("dropped", dropped).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(dropped).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SpanCount) == false) {
            return false;
        }
        SpanCount rhs = ((SpanCount) other);
        return new EqualsBuilder().append(dropped, rhs.dropped).isEquals();
    }

    @Override
    public void resetState() {
        dropped.resetState();
    }
}
