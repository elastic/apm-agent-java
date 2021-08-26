/*
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
 */
package co.elastic.apm.agent.jedis;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.redis.RedisSpanUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.BinaryJedis;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class JedisInstrumentation extends TracerAwareInstrumentation {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object beforeSendCommand(@Advice.This(typing = Assigner.Typing.DYNAMIC) BinaryJedis thiz,
                                           @Advice.Origin("#m") String method) {
        Span span = RedisSpanUtils.createRedisSpan(method);
        if (span != null) {
            span.getContext().getDestination()
                .withAddress(thiz.getClient().getHost())
                .withPort(thiz.getClient().getPort());
        }
        return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void afterSendCommand(@Nullable @Advice.Enter Object spanObj,
                                         @Nullable @Advice.Thrown Throwable thrown) {
        Span span = (Span) spanObj;
        if (span != null) {
            span.captureException(thrown)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("redis.clients.jedis.Jedis")
            .or(named("redis.clients.jedis.BinaryJedis"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isMethod()
            .and(isPublic())
            .and(
                isOverriddenFrom(nameEndsWith("Commands"))
                    .and(not(nameEndsWith("ClusterCommands")))
            );
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "jedis");
    }

}
