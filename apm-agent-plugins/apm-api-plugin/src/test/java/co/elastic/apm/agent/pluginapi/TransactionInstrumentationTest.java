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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.AbstractApiTest;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.api.AbstractSpanImplAccessor;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Outcome;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nullable;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionInstrumentationTest extends AbstractApiTest {

    private static final SecureRandom random = new SecureRandom();

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = ElasticApm.startTransaction();
        transaction.setType("default");
    }

    @Test
    void testSetName() {
        transaction.setName("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("foo");
    }

    @Test
    void testFrameworkName() {
        endTransaction();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");
    }

    @Test
    void testSetUserFrameworkValidNameBeforeSetByInternalAPI() {
        transaction.setFrameworkName("foo");
        AbstractSpanImplAccessor.accessTransaction(transaction).setFrameworkName("bar");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("foo");
    }

    @Test
    void testSetUserFrameworkValidNameAfterSetByInternalAPI() {
        AbstractSpanImplAccessor.accessTransaction(transaction).setFrameworkName("bar");
        transaction.setFrameworkName("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("foo");
    }

    static String[] invalidFrameworkNames() {
        return new String[]{null, ""};
    }

    @ParameterizedTest
    @MethodSource("invalidFrameworkNames")
    void testSetUserFrameworkInvalidNameBeforeSetByInternalAPI(@Nullable String frameworkName) {
        transaction.setFrameworkName(frameworkName);
        AbstractSpanImplAccessor.accessTransaction(transaction).setFrameworkName("bar");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidFrameworkNames")
    void testSetUserFrameworkInvalidNameAfterSetByInternalAPI(@Nullable String frameworkName) {
        AbstractSpanImplAccessor.accessTransaction(transaction).setFrameworkName("bar");
        transaction.setFrameworkName(frameworkName);
        endTransaction();
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isNull();
    }

    @Test
    void testSetType() {
        transaction.setType("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("foo");
    }

    @Test
    void testAddOrSetLabel() {
        int randomInt = random.nextInt();
        boolean randomBoolean = random.nextBoolean();
        String randomString = RandomStringUtils.randomAlphanumeric(3);
        transaction.addLabel("foo", "bar");
        transaction.setLabel("stringKey", randomString);
        transaction.setLabel("numberKey", randomInt);
        transaction.setLabel("booleanKey", randomBoolean);

        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("stringKey")).isEqualTo(randomString);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("numberKey")).isEqualTo(randomInt);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("booleanKey")).isEqualTo(randomBoolean);
    }

    @Test
    void testSetUser() {
        transaction.setUser("foo", "bar", "baz", "abc");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getUser().getDomain()).isEqualTo("abc");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
    }

    @Test
    void testSetUserWithoutDomain() {
        transaction.setUser("foo", "bar", "baz");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getUser().getDomain()).isNull();
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
    }

    @Test
    void testResult() {
        assertThat(transaction.setResult("foo")).isSameAs(transaction);
        endTransaction();
        checkResult("foo");
    }

    @Test
    void testInstrumentationDoesNotOverrideUserResult() {
        transaction.setResult("foo");
        endTransaction();
        reporter.getFirstTransaction().withResultIfUnset("200");
        checkResult("foo");
    }

    @Test
    void testUserCanOverrideResult() {
        transaction.setResult("foo");
        transaction.setResult("bar");
        endTransaction();
        checkResult("bar");
    }

    private void checkResult(String expected) {
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo(expected);
    }


    @Test
    void testChaining() {
        int randomInt = random.nextInt();
        boolean randomBoolean = random.nextBoolean();
        String randomString = RandomStringUtils.randomAlphanumeric(3);

        transaction.setType("foo")
            .setName("foo")
            .addLabel("foo", "bar")
            .setLabel("stringKey", randomString)
            .setLabel("numberKey", randomInt)
            .setLabel("booleanKey", randomBoolean)
            .setUser("foo", "bar", "baz", "abc")
            .setResult("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getDomain()).isEqualTo("abc");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("stringKey")).isEqualTo(randomString);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("numberKey")).isEqualTo(randomInt);
        assertThat(reporter.getFirstTransaction().getContext().getLabel("booleanKey")).isEqualTo(randomBoolean);
    }

    @Test
    public void startSpan() {
        // custom spans not part of shared spec
        reporter.disableCheckStrictSpanType();

        Span span = transaction.startSpan("foo", null, null);
        span.setName("bar");
        Span child = span.startSpan("foo2", null, null);
        child.setName("bar2");
        Span span3 = transaction.startSpan("foo3", null, null);
        span3.setName("bar3");
        span3.end();
        child.end();
        span.end();
        endTransaction();
        assertThat(reporter.getSpans()).hasSize(3);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("foo3");
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("bar3");
        assertThat(reporter.getSpans().get(1).getType()).isEqualTo("foo2");
        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("bar2");
        assertThat(reporter.getSpans().get(1).getTraceContext().getParentId()).isEqualTo(reporter.getSpans().get(2).getTraceContext().getId());
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }

    @Test
    public void testAgentPaused() {
        // end current transaction first
        endTransaction();
        reporter.resetWithoutRecycling();

        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();

        Transaction transaction = ElasticApm.startTransaction();
        transaction.setType("default").setName("transaction");
        transaction.startSpan("test", "agent", "paused").setName("span").end();
        transaction.end();

        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    public void testGetErrorIdWithTransactionCaptureException() {
        String errorId = null;
        try {
            throw new RuntimeException("test exception");
        } catch (Exception e) {
            errorId = transaction.captureException(e);
        }
        endTransaction();
        assertThat(errorId).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getOutcome()).isEqualTo(convertOutcome(Outcome.FAILURE));
    }

    @Test
    public void testGetErrorIdWithSpanCaptureException() {
        String errorId = null;
        Span span = transaction.startSpan("custom", null, null);
        span.setName("bar");
        try {
            throw new RuntimeException("test exception");
        } catch (Exception e) {
            errorId = span.captureException(e);
        } finally {
            span.end();
        }
        endTransaction();
        assertThat(errorId).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getOutcome()).isEqualTo(convertOutcome(Outcome.SUCCESS));
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getOutcome()).isEqualTo(convertOutcome(Outcome.FAILURE));
    }

    @Test
    void setOutcome_unknown() {
        reporter.disableCheckUnknownOutcome();

        testSetOutcome(Outcome.UNKNOWN);
    }

    @Test
    void setOutcome_failure() {
        testSetOutcome(Outcome.FAILURE);
    }

    @Test
    void setOutcome_success() {
        testSetOutcome(Outcome.SUCCESS);
    }

    private void testSetOutcome(Outcome outcome) {
        // set it first to a different value than the expected one
        Outcome[] values = Outcome.values();
        transaction.setOutcome(values[(outcome.ordinal() + 1) % values.length]);

        // only the last value set should be kept
        transaction.setOutcome(outcome);
        endTransaction();

        // test on enum names to avoid importing the two Outcome enums
        assertThat(reporter.getFirstTransaction().getOutcome().name())
            .isEqualTo(outcome.name());
    }

    private void endTransaction() {
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    private co.elastic.apm.agent.impl.transaction.Outcome convertOutcome(Outcome apiOutcome) {
        return co.elastic.apm.agent.impl.transaction.Outcome.valueOf(apiOutcome.toString());
    }
}
