package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.netty.util.AttributeMap;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public abstract class NettyInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<NettyContextHelper<AttributeMap>> nettyContextHelper;

    public NettyInstrumentation(ElasticApmTracer tracer) {
        nettyContextHelper = HelperClassManager.ForAnyClassLoader.of(tracer, "co.elastic.apm.agent.netty.NettyContextUtil");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }
}
