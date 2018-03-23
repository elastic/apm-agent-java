package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.atomic.AtomicInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dropped implements Recyclable {

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    @JsonProperty("total")
    private final AtomicInteger total = new AtomicInteger();

    /**
     * Number of spans that have been dropped by the agent recording the transaction.
     */
    @JsonProperty("total")
    public int getTotal() {
        return total.get();
    }

    /**
     * Increments the number of spans that have been dropped by the agent recording the transaction.
     */
    public Dropped increment() {
        this.total.incrementAndGet();
        return this;
    }

    @Override
    public void resetState() {
        total.set(0);
    }
}
