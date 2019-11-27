/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ChannelWriteContextStoringInstrumentation extends NettyInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ChannelWriteContextStoringInstrumentation.class);

    public ChannelWriteContextStoringInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel")
            .and(hasSuperType(named("io.netty.channel.Channel$Unsafe")));
    }

    /**
     * <ul>
     *     <li>{@link io.netty.channel.ChannelOutboundInvoker#write(Object)}</li>
     *     <li>{@link io.netty.channel.ChannelOutboundInvoker#write(Object, ChannelPromise)}</li>
     *     <li>{@link io.netty.channel.ChannelOutboundInvoker#writeAndFlush(Object)}</li>
     *     <li>{@link io.netty.channel.ChannelOutboundInvoker#writeAndFlush(Object, ChannelPromise)}</li>
     * </ul>
     * invoked before {@link TracingHttpClientHandler#write(io.netty.channel.ChannelHandlerContext, java.lang.Object, io.netty.channel.ChannelPromise)}
     *
     * {@link AbstractChannel#doWrite(io.netty.channel.ChannelOutboundBuffer)}
     * only executed on flush, writes only write into buffer
     *
     * {@link Channel.Unsafe#write(java.lang.Object, io.netty.channel.ChannelPromise)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("write")
            .and(isPublic())
            .and(returns(void.class))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, named("io.netty.channel.ChannelPromise")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return AdviceClass.class;
    }

    public static class AdviceClass {

        /**
         * When {@link ChannelPipeline#write} is executed with an active {@link TraceContextHolder},
         * store the trace context in an {@link Attribute}.
         */
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeWrite(@Advice.Origin Class<?> clazz, @Advice.Argument(1) ChannelPromise promise) {
            if (nettyContextHelper != null) {
                NettyContextHelper<Channel> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                if (helper != null) {
                    logger.debug("Channel.Unsafe#write storing context");
                    helper.storeContext(promise.channel());
                }
            }
        }
    }

}
