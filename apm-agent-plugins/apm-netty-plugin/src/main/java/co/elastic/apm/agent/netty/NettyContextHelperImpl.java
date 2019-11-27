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
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@VisibleForAdvice
public class NettyContextHelperImpl implements NettyContextHelper<Channel> {

    private static final String ATTR_TRACE_CONTEXT = "co.elastic.apm.trace_context";
    private static final Logger logger = LoggerFactory.getLogger(NettyContextHelperImpl.class);

    @VisibleForAdvice
    public static final WeakConcurrentMap<Channel, Boolean> enabledChannels = new WeakConcurrentMap.WithInlinedExpunction<>();

    /**
     * Gets the {@code co.elastic.apm.trace_context} attribute from the provided {@link AttributeMap} and activates the span on the thread.
     * @param channel
     * @return
     */
    @Nullable
    @Override
    @VisibleForAdvice
    public TraceContextHolder<?> restoreContext(Channel channel) {
        if (!isEnabled(channel)) {
            return null;
        }
        TraceContextHolder<?> active = ElasticApmInstrumentation.getActive();
        if (active != null) {
            return null;
        }
        // don't remove from attributeMap as there may be multiple reads for a single response (for example chunked http response)
        TraceContextHolder<?> context = channel
            .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT))
            .get();
        if (context != null) {
            logger.debug("restore context: {}", context);
            context.activate();
        }
        return context;
    }

    @Override
    @VisibleForAdvice
    public void storeContext(Channel channel) {
        if (!isEnabled(channel)) {
            return;
        }
        TraceContextHolder<?> active = ElasticApmInstrumentation.getActive();
        if (active == null) {
            return;
        }
        Attribute<TraceContextHolder<?>> attr = channel
            .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT));
        logger.debug("store context: {}", active);
        if (active instanceof AbstractSpan) {
            // keep the span alive as long as it's in the attribute map
            ((AbstractSpan) active).incrementReferences();
        }
        TraceContextHolder<?> previousContext = attr.getAndSet(active);
        if (previousContext instanceof AbstractSpan) {
            ((AbstractSpan) previousContext).decrementReferences();
        }
    }

    /**
     * @param channel
     */
    @Override
    @VisibleForAdvice
    public void removeContext(Channel channel) {
        if (!isEnabled(channel)) {
            return;
        }
        TraceContextHolder<?> previousContext = channel
            .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT))
            .getAndSet(null);
        if (previousContext instanceof AbstractSpan) {
            logger.debug("remove context");
            ((AbstractSpan) previousContext).decrementReferences();
        }
    }

    private boolean isEnabled(Channel channel) {
        Boolean enabled = enabledChannels.get(channel);
        if (enabled == null) {
            enabled = Boolean.FALSE;
            for (ChannelHandler handler : channel.pipeline().toMap().values()) {
                TypeDescription handlerDescription = TypeDescription.ForLoadedType.of(handler.getClass());
                for (ElementMatcher<TypeDescription> handlerMatcher : NettyInstrumentation.handlerMatchers) {
                    if (handlerMatcher.matches(handlerDescription)) {
                        enabled = Boolean.TRUE;
                        break;
                    }
                }
            }
            enabledChannels.put(channel, enabled);
        }
        return enabled;
    }
}
