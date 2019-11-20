package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.util.AttributeMap;

import javax.annotation.Nullable;

/**
 * @param <A> {@link AttributeMap}
 */
public interface NettyContextHelper<A> {

    @Nullable
    TraceContextHolder<?> restoreContext(A attributeMap);

    void storeContext(A attributeMap);

    void removeContext(A attributeMap);
}
