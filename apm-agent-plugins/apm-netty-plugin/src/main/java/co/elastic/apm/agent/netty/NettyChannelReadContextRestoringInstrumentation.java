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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class NettyChannelReadContextRestoringInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final String ATTR_TRACE_CONTEXT = "co.elastic.apm.trace_context";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel")
            .and(hasSuperType(named("io.netty.channel.ChannelPipeline")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fireChannelRead")
            .and(takesArguments(Object.class))
            .and(returns(named("io.netty.channel.ChannelPipeline")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    /**
     * When {@linkplain ChannelPipeline#fireChannelRead(Object) reading} from the channel,
     * see if we can restore the context form the {@link io.netty.util.AttributeMap}
     * which may have been set by a previous {@link ChannelPipeline#write(Object)}
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeFireChannelRead(@Advice.This ChannelPipeline channelPipeline,
                                              @Advice.Local("context") TraceContextHolder<?> context) {
        if (tracer != null) {
            TraceContextHolder<?> active = tracer.getActive();
            if (active == null) {
                context = channelPipeline
                    .channel()
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

    /**
     * Deactivate the context after the {@link ChannelPipeline#fireChannelRead(Object)}
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void afterFireChannelRead(@Nullable @Advice.Local("context") TraceContextHolder<?> context) {
        if (tracer != null && context != null) {
            tracer.deactivate(context);
        }
    }
}
