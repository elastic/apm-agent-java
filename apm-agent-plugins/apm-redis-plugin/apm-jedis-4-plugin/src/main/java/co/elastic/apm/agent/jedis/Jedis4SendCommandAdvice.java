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

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.redis.RedisSpanUtils;
import net.bytebuddy.asm.Advice;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.DefaultJedisSocketFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisSocketFactory;

import javax.annotation.Nullable;

public class Jedis4SendCommandAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object sendCommandEntry(@Advice.Argument(0) CommandArguments commandArguments) {
        return RedisSpanUtils.createRedisSpan(commandArguments.getCommand().toString());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void sendCommandExit(@Advice.FieldValue("socketFactory") JedisSocketFactory socketFactory,
                                       @Nullable @Advice.Enter Object spanObj,
                                       @Nullable @Advice.Thrown Throwable thrown) {
        Span<?> span = (Span<?>) spanObj;
        if (span != null) {
            if (socketFactory instanceof DefaultJedisSocketFactory) {
                HostAndPort hostAndPort = ((DefaultJedisSocketFactory) socketFactory).getHostAndPort();
                span.getContext().getDestination()
                    .withAddress(hostAndPort.getHost())
                    .withPort(hostAndPort.getPort());
            }
            span.captureException(thrown)
                .deactivate()
                .end();
        }
    }
}
