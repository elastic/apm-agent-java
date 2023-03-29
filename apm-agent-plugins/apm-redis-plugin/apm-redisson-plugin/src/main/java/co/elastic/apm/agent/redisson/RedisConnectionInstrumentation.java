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
package co.elastic.apm.agent.redisson;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.redis.RedisSpanUtils;
import io.netty.channel.Channel;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;


public class RedisConnectionInstrumentation extends TracerAwareInstrumentation {

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(inline = false)
        public static Object beforeSend(@Advice.This RedisConnection connection,
                                       @Advice.Argument(0) Object args) {
            Span<?> span = RedisSpanUtils.createRedisSpan("");
            if (span != null) {
                // get command
                if (args instanceof CommandsData) {
                    List<CommandData<?, ?>> commands = ((CommandsData) args).getCommands();
                    if (commands != null && !commands.isEmpty()) {
                        span.appendToName(commands.get(0).getCommand().getName()).appendToName("... [bulk]");
                    }
                } else if (args instanceof CommandData) {
                    span.appendToName(((CommandData<?, ?>) args).getCommand().getName());
                }

                // get connection address
                Channel channel = connection.getChannel();
                InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
                span.getContext().getDestination()
                    .withInetAddress(remoteAddress.getAddress())
                    .withPort(remoteAddress.getPort());
            }
            return span;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterSend(@Nullable @Advice.Enter Object spanObj,
                                     @Nullable @Advice.Thrown Throwable thrown) {
            Span<?> span = (Span<?>) spanObj;
            if (span != null) {
                span.captureException(thrown)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.redisson.client.RedisConnection")
            // this method only exist in version: 2.1.5 or higher
            .and(declaresMethod(named("getChannel")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("send");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "redisson");
    }

}
