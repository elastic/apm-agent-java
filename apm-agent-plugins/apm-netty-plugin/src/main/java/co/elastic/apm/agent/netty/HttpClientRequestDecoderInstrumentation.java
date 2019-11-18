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
import co.elastic.apm.agent.impl.transaction.Span;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link HttpClientCodec.Decoder#decode(io.netty.channel.ChannelHandlerContext, io.netty.buffer.ByteBuf, java.util.List)}
 *
 * Can be executed multiple times for the same HTTP response, for example in case of chunked responses.
 */
public class HttpClientRequestDecoderInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(HttpClientRequestDecoderInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.netty.handler.codec.http.HttpObjectDecoder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("decode")
            .and(takesArguments(3))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, named("io.netty.buffer.ByteBuf")))
            .and(takesArgument(2, named("java.util.List")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void afterDecode(@Advice.Argument(0) ChannelHandlerContext ctx,
                                    @Advice.Argument(2) List<Object> out) {
        logger.debug("HttpObjectDecoder#decode");
        Attribute<Span> spanAttr = ctx.channel().attr(AttributeKey.<Span>valueOf("elastic.apm.trace_context.client"));
        Span httpSpan = spanAttr.get();
        if (httpSpan == null) {
            return;
        }
        for (int i = 0, size = out.size(); i < size; i++) {
            Object msg = out.get(i);
            if (msg instanceof HttpResponse) {
                httpSpan.getContext().getHttp().withStatusCode(((HttpResponse) msg).status().code());
            }
            if (msg instanceof LastHttpContent) {
                spanAttr.set(null);
                httpSpan.end();
                logger.debug("HttpObjectDecoder#decode remove context");
                NettyContextUtil.removeContext(ctx.channel());
            }
        }
    }
}
