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
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments {@link HttpRequestEncoder#write(ChannelHandlerContext, Object, ChannelPromise)}
 * <p>
 * A common way of doing tracing for Netty is to add {@link io.netty.channel.ChannelInboundHandler}
 * and {@link io.netty.channel.ChannelOutboundHandler} to the {@link io.netty.channel.ChannelPipeline}.
 * </p>
 * <p>
 * But we rather instrument the {@link HttpRequestEncoder} and {@link HttpRequestDecoder} directly because we support runtime attachment.
 * So we would miss the opportunity to add our handlers if we attach the agent after the {@link io.netty.channel.ChannelPipeline}
 * has been initialized.
 * </p>
 */
public class HttpClientRequestEncoderInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.netty.handler.codec.MessageToMessageEncoder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("write")
            .and(takesArguments(3))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, named("java.lang.Object")))
            .and(takesArgument(2, named("io.netty.channel.ChannelPromise")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onBeforeWrite(@Advice.This ChannelOutboundHandler thiz,
                         @Advice.Argument(0) ChannelHandlerContext ctx,
                         @Advice.Argument(1) Object msg,
                         @Nullable @Advice.Local("span") Span span) throws Exception {
        if (!(thiz instanceof HttpRequestEncoder) || !(msg instanceof HttpRequest) || tracer == null) {
            return;
        }
        System.out.println("HttpRequestEncoder#write");
        HttpRequest request = (HttpRequest) msg;
        final TraceContextHolder<?> parent = tracer.getActive();
        if (parent != null && !parent.isExit()) {
            StringBuilder url = new StringBuilder();
            String host;
            if (request.uri().startsWith("http")) {
                url.append(request.uri());
                host = new URI(request.uri()).getHost();
            } else {
                String hostHeader = request.headers().get("host");
                int colon = hostHeader.lastIndexOf(':');
                host = hostHeader.substring(0, colon > 0 ? colon : hostHeader.length());
                url.append("http://").append(hostHeader).append(request.uri());
            }
            span = HttpClientHelper.startHttpClientSpan(parent, request.method().name(), url.toString(), host);
            if (span != null) {
                ctx.channel().attr(AttributeKey.<Span>valueOf("elastic.apm.trace_context.client")).set(span);
                request.headers().add(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onAfterWrite(@Advice.Argument(0) ChannelHandlerContext ctx,
                              @Nullable @Advice.Local("span") Span span,
                              @Nullable @Advice.Thrown Throwable thrown) {
        if (thrown != null && span != null) {
            ctx.channel().attr(AttributeKey.<Span>valueOf("elastic.apm.trace_context.client")).set(null);
            span.captureException(thrown).end();
        }
    }
}
