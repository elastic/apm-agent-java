/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.reactor_netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class HttpTrafficHandlerChannelReadInstrumentation extends ElasticApmInstrumentation {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(HttpTrafficHandlerChannelReadInstrumentation.class);

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void channelRead(@Advice.Argument(value = 0) ChannelHandlerContext chc,
                                   @Advice.Argument(value = 1) Object msg) {
        if (tracer == null) {
            logger.trace("channelRead tracer == null");
            return;
        }
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String method =request.method().name();
            String path = request.uri();
            String name =  method + " " + path;
            Transaction transaction = tracer.startRootTransaction(chc.getClass().getClassLoader())
                .withName(name)
                .withType("serverRequest");
            transaction.activate();
            TransactionHolder.put(chc,transaction);
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("reactor.netty.http.server.HttpTrafficHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("channelRead")
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, named("java.lang.Object")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("reactor-netty-channelRead-handler");
    }
}
