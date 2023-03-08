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
package co.elastic.apm.agent.lettuce;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import com.lambdaworks.redis.protocol.RedisCommand;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class Lettuce34Instrumentation extends TracerAwareInstrumentation {

    static final WeakMap<RedisCommand<?, ?, ?>, Span<?>> commandToSpan = WeakConcurrentProviderImpl.createWeakSpanMap();

    /**
     * We don't support Lettuce up to version 3.3, as the {@link RedisCommand#getType()} method is missing
     */
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // avoid instrumenting Lettuce <= 3.3 by requiring a type that has been introduced in 3.4
        return classLoaderCanLoadClass("com.lambdaworks.redis.event.EventBus")
            // EventBus is not available in Lettuce 4.x, so check for a type introduced in 4.0
            .or(classLoaderCanLoadClass("com.lambdaworks.redis.api.sync.RedisServerCommands"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

}
