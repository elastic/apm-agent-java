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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments
 * <ul>
 *     <li>{@link Connection#createChannel}</li>
 * </ul>
 */
public class ConnectionInstrumentation extends BaseInstrumentation {

    private static final Collection<Class<? extends ElasticApmInstrumentation>> CHANNEL_INSTRUMENTATIONS =
        Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
            ChannelInstrumentation.BasicPublish.class,
            ChannelInstrumentation.BasicConsume.class
        );

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // this fast heuristic works for all implementations within driver
        return nameEndsWith("Connection")
            .and(nameStartsWith("com.rabbitmq.client.impl"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // fine to use super type matching thanks to restricting with pre-filter
        return hasSuperType(named("com.rabbitmq.client.Connection"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createChannel");
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                              @Advice.Return @Nullable Channel channel) {

        if (thrown != null || channel == null) {
            return;
        }
        DynamicTransformer.Accessor.get().ensureInstrumented(channel.getClass(), CHANNEL_INSTRUMENTATIONS);
    }

}
