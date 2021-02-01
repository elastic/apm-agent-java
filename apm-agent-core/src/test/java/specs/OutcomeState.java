package specs;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;

import static org.assertj.core.api.Assertions.assertThat;

public class OutcomeState {

    private final ElasticApmTracer tracer;
    private Transaction transaction;
    private Span span;

    public OutcomeState() {
        tracer = MockTracer.createRealTracer();
    }

    public ElasticApmTracer getTracer() {
        return tracer;
    }

    public void startRootTransactionIfRequired(){
        if (transaction == null) {
            startTransaction();
        }
    }

    public Transaction startTransaction() {
        assertThat(transaction)
            .describedAs("transaction already set")
            .isNull();
        transaction = tracer.startRootTransaction(getClass().getClassLoader());

        return transaction;
    }


    public Span startSpan(){
        assertThat(span)
            .describedAs("span already set b")
            .isNull();

        assertThat(transaction)
            .describedAs("transaction required to create span")
            .isNotNull();

        span = transaction.createSpan();
        return span;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Span getSpan() {
        return span;
    }

}
