
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Any other arbitrary data captured by the agent, optionally provided by the user
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpanContext implements Recyclable {

    /**
     * An object containing contextual data for database spans
     */
    @JsonProperty("db")
    private final Db db = new Db();

    /**
     * An object containing contextual data for database spans
     */
    @JsonProperty("db")
    public Db getDb() {
        return db;
    }

    @Override
    public void resetState() {
        db.resetState();
    }
}
