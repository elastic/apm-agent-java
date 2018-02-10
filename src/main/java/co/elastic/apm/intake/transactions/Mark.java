
package co.elastic.apm.intake.transactions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * A mark captures the timing in milliseconds of a significant event during the lifetime of a transaction. Every mark is a simple key value pair, where the value has to be a number, and can be set by the user or the agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class Mark {


    @Override
    public String toString() {
        return new ToStringBuilder(this).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Mark) == false) {
            return false;
        }
        Mark rhs = ((Mark) other);
        return new EqualsBuilder().isEquals();
    }

}
