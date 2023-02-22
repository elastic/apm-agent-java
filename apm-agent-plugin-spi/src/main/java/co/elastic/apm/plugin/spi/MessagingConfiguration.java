package co.elastic.apm.plugin.spi;

import java.util.Collection;
import java.util.List;

public interface MessagingConfiguration {
    List<? extends WildcardMatcher> getIgnoreMessageQueues();

    Collection<String> getJmsListenerPackages();

    boolean shouldEndMessagingTransactionOnPoll();

    boolean shouldCollectQueueAddress();

    boolean isMessageTransactionPolling();

    boolean isMessageTransactionHandling();

    boolean isMessageBatchHandling();
}
