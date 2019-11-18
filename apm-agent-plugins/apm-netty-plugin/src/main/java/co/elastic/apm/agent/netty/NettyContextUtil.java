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
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@VisibleForAdvice
public class NettyContextUtil {

    private static final String ATTR_TRACE_CONTEXT = "co.elastic.apm.trace_context";
    private static final Logger logger = LoggerFactory.getLogger(NettyContextUtil.class);

    /**
     * Gets the {@code co.elastic.apm.trace_context} attribute from the provided {@link AttributeMap} and activates the span on the thread.
     * @param attributeMap
     * @return
     */
    @Nullable
    @VisibleForAdvice
    public static TraceContextHolder<?> restoreContext(AttributeMap attributeMap) {
        TraceContextHolder<?> context = null;
        TraceContextHolder<?> active = ElasticApmInstrumentation.getActive();
        if (active == null) {
            // don't remove from attributeMap as there may be multiple reads for a single response (for example chunked http response)
            context = attributeMap
                .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT))
                .get();
            if (context != null) {
                logger.debug("restore context: {}", context);
                context.activate();
            }
        }
        return context;
    }

    @VisibleForAdvice
    public static void storeContext(AttributeMap attributeMap) {
        TraceContextHolder<?> active = ElasticApmInstrumentation.getActive();
        if (active != null) {
            Attribute<TraceContextHolder<?>> attr = attributeMap
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
    }

    /**
     * @param attributeMap
     */
    @VisibleForAdvice
    public static void removeContext(AttributeMap attributeMap) {
        TraceContextHolder<?> previousContext = attributeMap
            .attr(AttributeKey.<TraceContextHolder<?>>valueOf(ATTR_TRACE_CONTEXT))
            .getAndSet(null);
        if (previousContext instanceof AbstractSpan) {
            logger.debug("remove context");
            ((AbstractSpan) previousContext).decrementReferences();
        }
    }
}
