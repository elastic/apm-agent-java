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
package co.elastic.apm.agent.redis;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;

import javax.annotation.Nullable;

public class RedisSpanUtils {
    @Nullable
    public static Span<?> createRedisSpan(String command) {
        AbstractSpan<?> activeSpan = GlobalTracer.get().getActive();
        if (activeSpan == null) {
            return null;
        }

        Span<?> span = activeSpan.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withName(command)
            .withType("db")
            .withSubtype("redis")
            .withAction("query");
        span.getContext().getServiceTarget()
            .withType("redis");
        return span.activate();
    }
}
