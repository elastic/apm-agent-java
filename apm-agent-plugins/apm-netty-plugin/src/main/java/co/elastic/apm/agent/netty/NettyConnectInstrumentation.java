package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.Channel;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.netty.NettyChannelReadContextRestoringInstrumentation.ATTR_TRACE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class NettyConnectInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel")
            .and(hasSuperType(named("io.netty.channel.nio.AbstractNioChannel$NioUnsafe")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    public static final class ConnectContextStoringInstrumentation extends NettyConnectInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("connect")
                .and(isOverriddenFrom(named("io.netty.channel.Channel$Unsafe")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeConnect(@Advice.This AbstractNioChannel channel) {
            System.out.println("connect");
            if (tracer != null) {
                TraceContextHolder<?> active = tracer.getActive();
                if (active != null) {
                    Attribute<TraceContextHolder<?>> attr = channel
                        .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT));
                    if (attr.get() == null) {
                        if (active instanceof AbstractSpan) {
                            ((AbstractSpan) active).incrementReferences();
                        }
                        attr.set(active);
                    }
                }
            }
        }
    }

    public static final class FinishConnectContextRestoringInstrumentation extends NettyConnectInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("finishConnect")
                .and(isOverriddenFrom(named("io.netty.channel.nio.AbstractNioChannel$NioUnsafe")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeFinishConnect(@Advice.This Channel channel,
                                                  @Advice.Local("context") TraceContextHolder<?> context) {
            System.out.println("beforeFinishConnect");
            if (tracer != null) {
                TraceContextHolder<?> active = tracer.getActive();
                if (active == null) {
                    context = channel
                        .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT))
                        .getAndSet(null);
                    if (context != null) {
                        tracer.activate(context);
                        if (context instanceof AbstractSpan) {
                            ((AbstractSpan) context).decrementReferences();
                        }
                    }
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void afterFinishConnect(@Nullable @Advice.Local("context") TraceContextHolder<?> context) {
            System.out.println("afterFinishConnect");
            if (tracer != null && context != null) {
                tracer.deactivate(context);
            }
        }

    }
}
