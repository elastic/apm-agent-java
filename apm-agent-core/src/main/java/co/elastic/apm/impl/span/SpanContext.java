
package co.elastic.apm.impl.span;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Any other arbitrary data captured by the agent, optionally provided by the user
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "db"
})
public class SpanContext implements Recyclable {

    /**
     * An object containing contextual data for database spans
     */
    @JsonProperty("db")
    @JsonPropertyDescription("An object containing contextual data for database spans")
    private final Db db = new Db();

    /**
     * An object containing contextual data for database spans
     */
    @JsonProperty("db")
    public Db getDb() {
        return db;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("db", db).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(db).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SpanContext) == false) {
            return false;
        }
        SpanContext rhs = ((SpanContext) other);
        return new EqualsBuilder().append(db, rhs.db).isEquals();
    }

    @Override
    public void resetState() {
        db.resetState();
    }
}
