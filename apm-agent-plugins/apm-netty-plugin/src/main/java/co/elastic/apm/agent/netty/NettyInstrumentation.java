package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.netty.channel.Channel;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class NettyInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<NettyContextHelper<Channel>> nettyContextHelper;

    @VisibleForAdvice
    public static CopyOnWriteArrayList<ElementMatcher<TypeDescription>> handlerMatchers = new CopyOnWriteArrayList<ElementMatcher<TypeDescription>>();

    public NettyInstrumentation(ElasticApmTracer tracer) {
        nettyContextHelper = HelperClassManager.ForAnyClassLoader.of(tracer, "co.elastic.apm.agent.netty.NettyContextHelperImpl");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    /**
     * By default, the context propagation for Netty {@link io.netty.channel.ChannelPipeline}s is disabled.
     * See {@link NettyContextHelperImpl#isEnabled(Channel)}.
     * Only if the {@link io.netty.channel.ChannelPipeline} contains a {@link io.netty.channel.ChannelHandler} which matches a particular
     * {@link ElementMatcher}, the generic context propagation will be enabled for this {@link Channel}.
     * <p>
     * This avoids problems when the {@link Channel} is used ways which are not expected by the generic context propagation which is
     * designed with client request/response cycles in mind.
     * </p>
     *
     * @param handlerMatcher enables context propagation for all {@link Channel}s whose {@link io.netty.channel.ChannelPipeline} contain a
     *                       {@link io.netty.channel.ChannelHandler} which matches this {@link ElementMatcher}
     */
    public static void enableContextPropagationForChannelPipelinesContainingHandler(ElementMatcher<TypeDescription> handlerMatcher) {
        handlerMatchers.add(handlerMatcher);
    }
}
