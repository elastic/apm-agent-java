
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
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Statement;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Matches {@link Connection#createStatement(String)} methods
 * and keeps a reference to from the resulting {@link Statement} to the connection.
 */
public abstract class R2dbcConnectionInstrumentation extends AbstractR2dbcInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Connection");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("io.r2dbc.spi.Connection")));
    }

    public static class CreateStatementInstrumentation extends R2dbcConnectionInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return nameStartsWith("createStatement")
                .and(returns(hasSuperType(named("io.r2dbc.spi.Statement"))))
                .and(takesArgument(0, String.class))
                .and(isPublic());
        }

        public static class AdviceClass {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void storeConnection(@Advice.This Object connectionObject,
                                               @Advice.Return @Nullable Statement statement,
                                               @Advice.Argument(0) String sql) {
                if (statement != null) {
                    R2dbcHelper.get().mapStatementToSql(statement, connectionObject, sql);
                }
            }
        }
    }

    public static class CreateBatchInstrumentation extends R2dbcConnectionInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return nameStartsWith("createBatch")
                .and(returns(hasSuperType(named("io.r2dbc.spi.Batch"))))
                .and(isPublic());
        }

        public static class AdviceClass {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void storeConnection(@Advice.This Object connectionObject,
                                               @Advice.Return @Nullable Batch batch) {
                if (batch != null) {
                    R2dbcHelper.get().mapBatch(batch, connectionObject);
                }
            }
        }
    }

}
