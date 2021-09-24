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
package co.elastic.apm.agent.r2dbc;

import co.elastic.apm.agent.r2dbc.helper.R2dbcHelper;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;


import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class R2dbcConnectionFactoryProviderInstrumentation extends AbstractR2dbcInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("io.r2dbc.spi.ConnectionFactoryProvider")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("create")
            .and(returns(hasSuperType(named("io.r2dbc.spi.ConnectionFactory"))))
            .and(takesArgument(0, named("io.r2dbc.spi.ConnectionFactoryOptions")))
            .and(isPublic());
    }

    public static class AdviceClass {
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void storeConnectionFactoryOptions(@Advice.Return ConnectionFactory connectionFactory,
                                                         @Advice.Argument(0) ConnectionFactoryOptions connectionFactoryOptions) {
            if (connectionFactory == null) {
                return;
            }
            R2dbcHelper helper = R2dbcHelper.get();
            helper.mapConnectionFactoryData(connectionFactory, connectionFactoryOptions);
        }
    }
}
