package co.elastic.apm.plugin.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EmptyMessagingConfiguration implements MessagingConfiguration {

    public static final MessagingConfiguration INSTANCE = new EmptyMessagingConfiguration();

    private EmptyMessagingConfiguration() {
    }

    @Override
    public List<? extends WildcardMatcher> getIgnoreMessageQueues() {
        return Collections.<WildcardMatcher>emptyList();
    }

    @Override
    public Collection<String> getJmsListenerPackages() {
        return Collections.<String>emptyList();
    }

    @Override
    public boolean shouldEndMessagingTransactionOnPoll() {
        return false;
    }

    @Override
    public boolean shouldCollectQueueAddress() {
        return false;
    }

    @Override
    public boolean isMessageTransactionPolling() {
        return true;
    }

    @Override
    public boolean isMessageTransactionHandling() {
        return true;
    }

    @Override
    public boolean isMessageBatchHandling() {
        return false;
    }
}
