
package co.elastic.apm.impl.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * The env variable is a compounded of environment information passed from the webserver.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Env {


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
        if ((other instanceof Env) == false) {
            return false;
        }
        Env rhs = ((Env) other);
        return new EqualsBuilder().isEquals();
    }

}
