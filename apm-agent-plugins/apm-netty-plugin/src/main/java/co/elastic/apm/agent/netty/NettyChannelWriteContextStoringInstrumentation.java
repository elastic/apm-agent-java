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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.netty.NettyChannelReadContextRestoringInstrumentation.ATTR_TRACE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class NettyChannelWriteContextStoringInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel")
            .and(hasSuperType(named("io.netty.channel.ChannelPipeline")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("write")
            .and(isPublic())
            .and(isOverriddenFrom(named("io.netty.channel.ChannelOutboundInvoker")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    /**
     * {@link ChannelPipeline#write} is executed on the same thread
     * Store the trace context in an {@link Attribute}
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeFireChannelWrite(@Advice.This ChannelPipeline channelPipeline) {
        if (tracer != null) {
            TraceContextHolder<?> active = tracer.getActive();
            if (active != null) {
                Attribute<TraceContextHolder<?>> attr = channelPipeline
                    .channel()
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
