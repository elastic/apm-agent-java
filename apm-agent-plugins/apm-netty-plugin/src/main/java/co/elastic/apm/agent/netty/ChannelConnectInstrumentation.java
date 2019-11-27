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
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.AbstractNioChannel.NioUnsafe;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Propagates the trace context when connecting to a channel.
 * As {@link NioUnsafe#finishConnect()} is called on a different thread than
 * {@link Channel.Unsafe#connect}, we have to propagate the context.
 *
 * Usually, the flow is to connect via {@link ChannelPipeline#connect} and then to register a listener to the returned {@link io.netty.channel.ChannelFuture}
 * via {@link io.netty.channel.ChannelFuture#addListener(GenericFutureListener)}.
 * Within that listener, the actual {@link Channel#write} operation is performed.
 */
public abstract class ChannelConnectInstrumentation extends NettyInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ChannelConnectInstrumentation.class);

    public ChannelConnectInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    /**
     * {@link NioUnsafe#connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)}
     */
    public static final class ConnectContextStoringInstrumentation extends ChannelConnectInstrumentation {

        public ConnectContextStoringInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameStartsWith("io.netty.channel")
                .and(hasSuperType(named("io.netty.channel.nio.AbstractNioChannel$NioUnsafe")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("connect")
                .and(returns(void.class))
                .and(takesArguments(3))
                .and(takesArgument(0, named("java.net.SocketAddress")))
                .and(takesArgument(1, named("java.net.SocketAddress")))
                .and(takesArgument(2, named("io.netty.channel.ChannelPromise")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return AdviceClass.class;
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class)
            private static void beforeConnect(@Advice.Origin Class<?> clazz, @Advice.Argument(2) ChannelPromise promise) {
                if (nettyContextHelper != null) {
                    NettyContextHelper<Channel> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                    if (helper != null) {
                        logger.debug("NioUnsafe#connect before: store context");
                        helper.storeContext(promise.channel());
                    }
                }
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
            private static void afterConnect(@Advice.Origin Class<?> clazz,
                                             @Nullable @Advice.Thrown Throwable t,
                                             @Advice.Argument(2) ChannelPromise promise) {
                if (t != null) {
                    if (nettyContextHelper != null) {
                        NettyContextHelper<Channel> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                        if (helper != null) {
                            logger.debug("NioUnsafe#connect error: remove context");
                            helper.removeContext(promise.channel());
                        }
                    }
                }
            }
        }
    }

    /**
     * {@link NioUnsafe#finishConnect()} resolves the {@link ChannelPromise}s by calling {@link ChannelPromise#trySuccess()}
     * and thereby invoking {@link io.netty.util.concurrent.GenericFutureListener#operationComplete(Future)}.
     *
     */
    public static final class FinishConnectContextRestoringInstrumentation extends ChannelConnectInstrumentation {

        public FinishConnectContextRestoringInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameStartsWith("io.netty.channel")
                .and(hasSuperType(named("io.netty.channel.nio.AbstractNioChannel$NioUnsafe")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("finishConnect")
                .and(returns(void.class))
                .and(takesArguments(0));
        }

        @Override
        public Class<?> getAdviceClass() {
            return AdviceClass.class;
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class)
            private static void beforeFinishConnect(@Advice.Origin Class<?> clazz,
                                                    @Advice.This NioUnsafe unsafe,
                                                    @Advice.Local("context") TraceContextHolder<?> context) {
                if (nettyContextHelper != null) {
                    NettyContextHelper<Channel> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                    if (helper != null) {
                        logger.debug("NioUnsafe#finishConnect before: restore and remove context");
                        Channel channel = unsafe.voidPromise().channel();
                        context = helper.restoreContext(channel);
                        helper.removeContext(channel);
                    }
                }
            }

            @Advice.OnMethodExit(suppress = Throwable.class)
            private static void afterFinishConnect(@Advice.Origin Class<?> clazz,
                                                   @Advice.This NioUnsafe unsafe,
                                                   @Nullable @Advice.Local("context") TraceContextHolder<?> context) {
                if (context != null) {
                    logger.debug("NioUnsafe#finishConnect after: deactivate");
                    context.deactivate();
                }
            }
        }
    }
}
