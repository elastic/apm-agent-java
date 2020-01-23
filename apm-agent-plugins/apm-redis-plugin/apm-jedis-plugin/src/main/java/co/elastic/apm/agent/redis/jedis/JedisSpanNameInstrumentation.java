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
package co.elastic.apm.agent.redis.jedis;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Protocol;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Sets the Redis span name to the Redis protocol command.
 * Because of the way the instrumentation works for Jedis,
 * it would otherwise be set to the {@link redis.clients.jedis.Jedis} client method name.
 * This is good enough as a default but we want all Redis clients to produce the same span names.
 */
public class JedisSpanNameInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodEnter
    private static void setSpanNameToRedisProtocolCommand(@Advice.Argument(1) Object command) {
        if (tracer != null) {
            TraceContextHolder<?> active = tracer.getActive();
            if (active instanceof Span) {
                Span activeSpan = (Span) active;
                if ("redis".equals(activeSpan.getSubtype())) {
                    activeSpan.withName(command.toString());
                }
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("redis.clients.jedis.Protocol");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("sendCommand")
            .and(isPublic())
            .and(takesArgument(0, nameEndsWith("RedisOutputStream")))
            .and(takesArgument(1, nameEndsWith("Command")))
            .and(takesArgument(2, byte[][].class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "jedis");
    }
}
