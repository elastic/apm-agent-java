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
import io.netty.channel.Channel;
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
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ChannelBeginReadContextStoringInstrumentation extends NettyInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ChannelBeginReadContextStoringInstrumentation.class);

    public ChannelBeginReadContextStoringInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel")
            .and(hasSuperType(named("io.netty.channel.Channel$Unsafe")));
    }

    /**
     * {@link Channel.Unsafe#beginRead()}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("beginRead")
            .and(isPublic())
            .and(returns(void.class))
            .and(takesArguments(0));
    }

    @Override
    public Class<?> getAdviceClass() {
        return AdviceClass.class;
    }

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeRead(@Advice.Origin Class<?> clazz, @Advice.This Channel.Unsafe thiz) {
            if (nettyContextHelper != null) {
                logger.debug("Channel.Unsafe#beginRead storing context");
                NettyContextHelper<Channel> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                if (helper != null) {
                    helper.storeContext(thiz.voidPromise().channel());
                }
            }
        }
    }
}
