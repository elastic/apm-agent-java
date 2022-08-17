package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class AbstractSQSClientIT extends AbstractAwsClientIT {
    protected CoreConfiguration coreConfiguration;
    protected MessagingConfiguration messagingConfiguration;

    @BeforeEach
    public void setupConfig() {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testReceiveOneMessageWithinTransaction(String clientType) {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);

        String queueUrl = setupQueue();

        Transaction parentTransaction = startTestRootTransaction("parent-transaction");
        sendMessage(queueUrl);
        parentTransaction.deactivate().end();

        try {
            // wait to have a message age > 0
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Transaction transaction = startTestRootTransaction("sqs-test");

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl);
        } else {
            receiveMessages(queueUrl);
        }

        transaction.deactivate().end();

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(4);

        Optional<Transaction> optTransaction = reporter.getTransactions().stream().filter(t -> t.getNameAsString().equals("sqs-test")).findFirst();
        assertThat(optTransaction.isPresent()).isTrue();
        Transaction sqsTransaction = optTransaction.get();

        Span receivingSpan = reporter.getSpanByName("SQS POLL from " + SQS_QUEUE_NAME);
        assertThat(receivingSpan.getContext().getMessage().getQueueName()).isEqualTo(SQS_QUEUE_NAME);
        assertThat(receivingSpan.getContext().getMessage().getAge()).isGreaterThan(0);
        assertThat(receivingSpan.getContext().getMessage().getBodyForRead()).isNotNull();
        assertThat(receivingSpan.getContext().getMessage().getBodyForRead().toString()).isEqualTo(MESSAGE_BODY);
        assertThat(receivingSpan.getParent()).isSameAs(sqsTransaction);

        Span processingSpan = reporter.getSpanByName(SQS_MESSAGE_PROCESSING_SPAN_NAME);
        assertThat(processingSpan.getType()).isEqualTo(SQS_MESSAGING_TYPE);
        assertThat(processingSpan.getSubtype()).isEqualTo(SQS_TYPE);
        assertThat(processingSpan.getAction()).isEqualTo(SQS_MESSAGE_PROCESSING_ACTION);

        assertThat(reporter.getSpanByName("custom-child-span").isChildOf(processingSpan)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testReceiveOneMessageOutsideATransaction(String clientType) {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);

        String queueUrl = setupQueue();

        Transaction parentTransaction = startTestRootTransaction("parent-transaction");
        sendMessage(queueUrl);
        parentTransaction.deactivate().end();

        try {
            // wait to have a message age > 0
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl);
        } else {
            receiveMessages(queueUrl);
        }

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(2);

        Optional<Transaction> optTransaction = reporter.getTransactions().stream().filter(t -> t.getNameAsString().startsWith("SQS RECEIVE from")).findFirst();
        assertThat(optTransaction.isPresent()).isTrue();
        Transaction sqsTransaction = optTransaction.get();

        Span childSpan = reporter.getSpanByName("custom-child-span");
        assertThat(childSpan.isChildOf(sqsTransaction)).isTrue();

        Span sendingSpan = reporter.getSpanByName("SQS SEND to " + SQS_QUEUE_NAME);

        assertThat(sqsTransaction.getContext().getMessage().getQueueName()).isEqualTo(SQS_QUEUE_NAME);
        assertThat(sqsTransaction.getContext().getMessage().getAge()).isGreaterThan(0);
        assertThat(sqsTransaction.getContext().getMessage().getBodyForRead()).isNotNull();
        assertThat(sqsTransaction.getContext().getMessage().getBodyForRead().toString()).isEqualTo(MESSAGE_BODY);
        assertThat(sqsTransaction.isChildOf(sendingSpan)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testReceiveMultipleMessagesOutsideATransaction(String clientType) {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);

        String queueUrl = setupQueue();

        Transaction parentTransaction = startTestRootTransaction("parent-transaction");
        sendMessage(queueUrl);
        sendMessage(queueUrl);
        sendMessage(queueUrl);
        parentTransaction.deactivate().end();

        try {
            // wait to have a message age > 0
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl, 3);
        } else {
            receiveMessages(queueUrl, 3);
        }

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(4);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(6);

        long numReceiveTransactions = reporter.getTransactions().stream().filter(t -> t.getNameAsString().startsWith("SQS RECEIVE from")).count();
        long numCustomSpans = reporter.getSpans().stream().filter(t -> t.getNameAsString().startsWith("custom")).count();
        long numSendSpans = reporter.getSpans().stream().filter(t -> t.getNameAsString().startsWith("SQS SEND")).count();

        assertThat(numSendSpans).isEqualTo(3);

        assertThat(numReceiveTransactions).isEqualTo(3);
        assertThat(numCustomSpans).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testReceiveMultipleMessagesWithinATransaction(String clientType) {
        when(coreConfiguration.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);

        String queueUrl = setupQueue();

        Transaction parentTransaction = startTestRootTransaction("parent-transaction");
        sendMessage(queueUrl);
        sendMessage(queueUrl);
        sendMessage(queueUrl);
        parentTransaction.deactivate().end();

        try {
            // wait to have a message age > 0
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Transaction transaction = startTestRootTransaction("sqs-test");

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl, 3);
        } else {
            receiveMessages(queueUrl, 3);
        }

        transaction.deactivate().end();

        assertThat(reporter.getNumReportedTransactions()).isEqualTo(2);
        assertThat(reporter.getNumReportedSpans()).isEqualTo(10);

        long numReceiveSpans = reporter.getSpans().stream().filter(t -> t.getNameAsString().startsWith("Process SQS message")).count();
        long numCustomSpans = reporter.getSpans().stream().filter(t -> t.getNameAsString().startsWith("custom")).count();
        long numSendSpans = reporter.getSpans().stream().filter(t -> t.getNameAsString().startsWith("SQS SEND")).count();
        reporter.getSpanByName("SQS POLL from some-test-sqs-queue");

        assertThat(numSendSpans).isEqualTo(3);
        assertThat(numReceiveSpans).isEqualTo(3);
        assertThat(numCustomSpans).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testQueueExclusion(String clientType) {
        when(messagingConfiguration.getIgnoreMessageQueues()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf(SQS_IGNORED_QUEUE_NAME)));

        String queueUrl = setupQueue();
        String ignoredQueueUrl = setupIgnoreQueue();

        Transaction transaction = startTestRootTransaction("sqs-test");

        if (clientType.equals("async")) {
            executeAsyncQueueExclusion(queueUrl, ignoredQueueUrl);
        } else {
            executeQueueExclusion(queueUrl, ignoredQueueUrl);
        }

        assertThat(reporter.getNumReportedSpans()).isEqualTo(2);
        reporter.getSpanByName("SQS SEND to " + SQS_QUEUE_NAME);
        reporter.getSpanByName("SQS POLL from " + SQS_QUEUE_NAME);

        transaction.deactivate().end();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testEmptyReceiveWithinTransaction(String clientType) {

        String queueUrl = setupQueue();

        Transaction transaction = startTestRootTransaction("sqs-test");

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl);
        } else {
            receiveMessages(queueUrl);
        }

        assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
        reporter.getSpanByName("SQS POLL from " + SQS_QUEUE_NAME);

        transaction.deactivate().end();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async"})
    public void testEmptyReceiveOutsideTransaction(String clientType) {
        String queueUrl = setupQueue();

        if (clientType.equals("async")) {
            receiveMessagesAsync(queueUrl);
        } else {
            receiveMessages(queueUrl);
        }

        assertThat(reporter.getNumReportedSpans()).isEqualTo(0);
        assertThat(reporter.getNumReportedTransactions()).isEqualTo(0);
    }

    protected void executeChildSpan() {
        AbstractSpan<?> active = tracer.getActive();
        assertThat(active).isNotNull();
        Span childSpan = active.createSpan();
        assertThat(childSpan).isNotNull();
        childSpan.withName("custom-child-span");
        childSpan.activate();
        childSpan.deactivate().end();
    }

    public abstract void sendMessage(String queueUrl);


    public void receiveMessages(String queueUrl) {
        receiveMessages(queueUrl, 1);
    }

    public void receiveMessagesAsync(String queueUrl) {
        receiveMessagesAsync(queueUrl, 1);
    }

    public abstract void receiveMessages(String queueUrl, int numMessages);

    public abstract void receiveMessagesAsync(String queueUrl, int numMessages);

    public abstract void executeQueueExclusion(String queueUrl, String ignoredQueueUrl);

    public abstract void executeAsyncQueueExclusion(String queueUrl, String ignoredQueueUrl);

    protected abstract String setupQueue();

    protected abstract String setupIgnoreQueue();
}
