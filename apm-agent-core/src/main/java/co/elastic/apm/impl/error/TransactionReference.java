
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


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
    public void resetState() {
        id.resetState();
    }
}
