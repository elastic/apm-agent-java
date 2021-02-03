package specs;

import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class OutcomeStepsDefinitions {

    private final OutcomeState state;

    public OutcomeStepsDefinitions(OutcomeState state) {
        this.state = state;
    }

    @Given("an active transaction")
    public void startTransaction() {
        assertThat(state.getTransaction()).isNull();

        state.startTransaction();
    }

    @Given("an active span")
    public void startSpan() {
        // spans can't exist outside of a transaction, thus we have to create it if not explicitly asked to
        state.startRootTransactionIfRequired();

        state.startSpan();
    }

    @Then("{} outcome is {string}")
    public void thenOutcomeIs(String context, String outcome) {
        checkOutcome(context.equals("span") ? state.getSpan() : state.getTransaction(), outcome);
    }

    @Then("user sets {} outcome to {string}")
    public void userSetOutcome(String context, String outcome) {
        setUserOutcome(getContext(context), outcome);
    }

    @Then("{} terminates with outcome {string}")
    public void terminatesWithOutcome(String context, String outcome) {
        endWithOutcome(getContext(context), outcome);
    }

    @Given("{} terminates with an error")
    public void terminatesWithError(String context) {
        getContext(context)
            .captureException(new Throwable())
            .end();
    }

    @Given("{} terminates without error")
    public void terminatesWithoutError(String context) {
        getContext(context).end();
    }

    AbstractSpan<?> getContext(String context) {
        return context.equals("span") ? state.getSpan() : state.getTransaction();
    }

    // HTTP spans & transactions mapping

    @Given("an HTTP span with {int} response code")
    public void httpSpanWithStatus(int code) {
        state.startRootTransactionIfRequired();
        Span span = state.startSpan();

        span.withName(String.format("HTTP span status = %d", code));
        span.withOutcome(ResultUtil.getOutcomeByHttpClientStatus(code))
            .end();

    }

    @Given("an HTTP transaction with {int} response code")
    public void httpTransactionWithStatus(int code) {
        Transaction transaction = state.startTransaction();

        transaction.withName(String.format("HTTP transaction status = %d", code));
        transaction.withOutcome(ResultUtil.getOutcomeByHttpServerStatus(code)).end();
    }

    // utilities

    static void endWithOutcome(AbstractSpan<?> context, String outcome) {
        assertThat(context).isNotNull();
        context.withOutcome(fromString(outcome))
            .end();
    }

    static void setUserOutcome(AbstractSpan<?> context, String outcome) {
        assertThat(context).isNotNull();
        context.withUserOutcome(fromString(outcome));
    }

    static void checkOutcome(AbstractSpan<?> context, String outcome) {
        assertThat(context).isNotNull();
        assertThat(context.getOutcome())
            .describedAs("expected outcome = %s for context = %s", outcome, context)
            .isEqualTo(fromString(outcome));
    }

    static Outcome fromString(String outcome) {
        return Outcome.valueOf(outcome.toUpperCase(Locale.ROOT));
    }


}
