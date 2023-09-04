package co.elastic.apm.agent.micronaut;

import co.elastic.apm.agent.tracer.Transaction;

public class PropagatedContextElement implements io.micronaut.core.propagation.PropagatedContextElement {
    private final Transaction<?> transaction;

    public PropagatedContextElement(Transaction<?> transaction) {
        this.transaction = transaction;
    }

    public Transaction<?> getTransaction() {
        return transaction;
    }
}
