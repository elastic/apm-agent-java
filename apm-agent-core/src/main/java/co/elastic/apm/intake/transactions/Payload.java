
package co.elastic.apm.intake.transactions;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
import co.elastic.apm.intake.System;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({
    "service",
    "process",
    "system",
    "transactions"
})
public class Payload implements Recyclable {

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    private final Service service;
    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    private final Process process;
    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    private final System system;
    /**
     * (Required)
     */
    @JsonProperty("transactions")
    private final List<Transaction> transactions = new ArrayList<Transaction>();

    public Payload(Service service, Process process, System system) {
        this.service = service;
        this.process = process;
        this.system = system;
    }

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    public Service getService() {
        return service;
    }

    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    public Process getProcess() {
        return process;
    }

    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    public System getSystem() {
        return system;
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
        if ((other instanceof Payload) == false) {
            return false;
        }
        Payload rhs = ((Payload) other);
        return new EqualsBuilder().append(process, rhs.process).append(system, rhs.system).append(transactions, rhs.transactions).append(service, rhs.service).isEquals();
    }

    @Override
    public void resetState() {
        transactions.clear();
    }

    public void recycle() {
        for (Transaction transaction : transactions) {
            transaction.recycle();
        }
    }
}
