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

    @Then("span outcome is {string}")
    public void spanOutcomeIsUnknown(String outcome) {
        checkOutcome(state.getSpan(), outcome);
    }

    @Then("transaction outcome is {string}")
    public void transactionOutcomeIsUnknown(String outcome) {
        checkOutcome(state.getTransaction(), outcome);
    }

    @Then("user sets span outcome to {string}")
    public void userSetsSpanOutcome(String outcome) {
        setUserOutcome(state.getSpan(), outcome);
    }

    @Then("user sets transaction outcome to {string}")
    public void userSetsTransactionOutcome(String outcome) {
        setUserOutcome(state.getTransaction(), outcome);
    }

    @Then("span terminates with outcome {string}")
    public void spanTerminatesWithOutcome(String outcome) {
        endWithOutcome(state.getSpan(), outcome);
    }

    @Then("transaction terminates with outcome {string}")
    public void transactionTerminatesWithOutcome(String outcome) {
        endWithOutcome(state.getTransaction(), outcome);
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

    // DB spans

    @Given("an active DB span without error")
    public void dbSpanWithoutError() {
        state.startRootTransactionIfRequired();
        Span span = state.startSpan().withType("db");

        span.end();
    }

    @Given("an active DB span with error")
    public void dbSpanWithError() {
        Throwable t = new Throwable();
        state.startRootTransactionIfRequired();

        Span span = state.startSpan()
            .withType("db")
            .captureException(t);

        span.end();
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
            .describedAs("expected outcome = %s for context = %s", outcome, context.getNameAsString())
            .isEqualTo(fromString(outcome));
    }

    static Outcome fromString(String outcome) {
        return Outcome.valueOf(outcome.toUpperCase(Locale.ROOT));
    }


}
