package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

import javax.annotation.Nullable;

/**
 * @param <C> {@link java.nio.channels.Channel}
 */
public interface NettyContextHelper<C> {

    @Nullable
    TraceContextHolder<?> restoreContext(C channel);

    void storeContext(C channel);

    void removeContext(C channel);
}
