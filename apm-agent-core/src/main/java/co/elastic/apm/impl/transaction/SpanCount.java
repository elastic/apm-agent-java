
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpanCount implements Recyclable {

    @JsonProperty("dropped")
    private final Dropped dropped = new Dropped();

    @JsonProperty("dropped")
    public Dropped getDropped() {
        return dropped;
    }

    @Override
    public void resetState() {
        dropped.resetState();
    }
}
