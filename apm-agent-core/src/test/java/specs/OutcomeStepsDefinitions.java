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
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class OutcomeStepsDefinitions {

    private final ScenarioState state;

    public OutcomeStepsDefinitions(ScenarioState state) {
        this.state = state;
    }

    @Given("the agent sets the {contextType} outcome to {string}")
    public void internalSetOutcome(String context, String outcome) {
        setInternalOutcome(state.getContext(context), fromString(outcome));
    }

    @Given("a user sets the {contextType} outcome to {string}")
    public void userSetOutcome(String context, String outcome) {
        setUserOutcome(state.getContext(context), fromString(outcome));
    }

    @Given("an error is reported to the {}")
    public void reportError(String context) {
        state.getContext(context).captureException(new Throwable());
    }

    @Then("the {contextType} outcome is {string}")
    public void thenOutcomeIs(String context, String outcome) {
        checkOutcome(context.equals("span") ? state.getSpan() : state.getTransaction(), fromString(outcome));
    }

    // HTTP spans & transactions mapping

    @Given("a HTTP call is made that returns {int}")
    public void httpSpanWithStatus(int code) {
        Span span = state.getSpan();
        span.withName(String.format("HTTP span status = %d", code));
        span.withOutcome(ResultUtil.getOutcomeByHttpClientStatus(code));
    }

    @Given("a HTTP call is received that returns {int}")
    public void httpTransactionWithStatus(int code) {
        Transaction transaction = state.getTransaction();
        transaction.withName(String.format("HTTP transaction status = %d", code));
        transaction.withOutcome(ResultUtil.getOutcomeByHttpServerStatus(code));
    }

    // utilities

    static void setUserOutcome(AbstractSpan<?> context, Outcome outcome) {
        assertThat(context).isNotNull();
        context.withUserOutcome(outcome);
    }

    static void setInternalOutcome(AbstractSpan<?> context, Outcome outcome) {
        assertThat(context).isNotNull();
        context.withOutcome(outcome);
    }

    static void checkOutcome(AbstractSpan<?> context, Outcome outcome) {
        assertThat(context).isNotNull();
        assertThat(context.getOutcome())
            .describedAs("expected outcome = %s for context = %s", outcome, context)
            .isEqualTo(outcome);
    }

    static Outcome fromString(String outcome) {
        return Outcome.valueOf(outcome.toUpperCase(Locale.ROOT));
    }

}
