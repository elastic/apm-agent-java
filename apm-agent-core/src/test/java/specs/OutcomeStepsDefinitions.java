/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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

    private final SpecTracerState state;

    public OutcomeStepsDefinitions(SpecTracerState state) {
        this.state = state;
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
