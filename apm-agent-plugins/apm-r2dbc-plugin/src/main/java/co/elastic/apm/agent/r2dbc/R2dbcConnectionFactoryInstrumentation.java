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
import co.elastic.apm.agent.r2dbc.helper.R2dbcReactorHelper;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

public class R2dbcConnectionFactoryInstrumentation extends AbstractR2dbcInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("io.r2dbc.spi.ConnectionFactory")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("create")
            .and(takesNoArguments())
            .and(isPublic());
    }

    public static class AdviceClass {

        @Nullable
        @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object onAfterExecute(@Advice.This ConnectionFactory connectionFactory,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.Return @Nullable Publisher<? extends Connection> returnValue) {
            if (t != null || returnValue == null) {
                return returnValue;
            }
            R2dbcHelper helper = R2dbcHelper.get();
            ConnectionFactoryOptions connectionFactoryOptions = helper.getConnectionFactoryOptions(connectionFactory);
            return R2dbcReactorHelper.wrapConnectionPublisher(returnValue, connectionFactoryOptions);
        }
    }
}
