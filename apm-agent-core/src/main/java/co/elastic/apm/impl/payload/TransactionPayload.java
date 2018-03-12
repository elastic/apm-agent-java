
package co.elastic.apm.impl.payload;

import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.List;


/**
 * Transactions payload
 * <p>
 * List of transactions wrapped in an object containing some other attributes normalized away from the transactions themselves
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionPayload extends Payload {

    /**
     * (Required)
     */
    @JsonProperty("transactions")
    private final List<Transaction> transactions = new ArrayList<Transaction>();

    public TransactionPayload(ProcessInfo process, Service service, SystemInfo system) {
        super(process, service, system);
    }

    /**
     * (Required)
     */
    @JsonProperty("transactions")
    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("service", service).append("process", process).append("system", system).append("transactions", transactions).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(process).append(system).append(transactions).append(service).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TransactionPayload) == false) {
            return false;
        }
        TransactionPayload rhs = ((TransactionPayload) other);
        return new EqualsBuilder().append(process, rhs.process).append(system, rhs.system).append(transactions, rhs.transactions).append(service, rhs.service).isEquals();
    }

    @Override
    public void resetState() {
        transactions.clear();
    }

    @Override
    public List<? extends Recyclable> getPayloadObjects() {
        return transactions;
    }

    @Override
    public void recycle() {
        for (Transaction transaction : transactions) {
            transaction.recycle();
        }
    }
}
