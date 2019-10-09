package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link Channel.Unsafe#disconnect(io.netty.channel.ChannelPromise)}
 */
public class ChannelDisconnectContextRemovingInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel.")
            .and(hasSuperType(named("io.netty.channel.Channel$Unsafe")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("disconnect")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.netty.channel.ChannelPromise")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    @Advice.OnMethodEnter
    private static void onBeforeDisconnect(@Advice.Argument(0) ChannelPromise promise) {
        NettyContextUtil.removeContext(promise.channel());
    }
}
