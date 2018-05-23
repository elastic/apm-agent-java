/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.jdbc;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class PreparedStatementInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    private static ElasticApmTracer tracer;
    @Nullable
    private static JdbcHelper jdbcEventListener;

    // not inlining as we can then set breakpoints into this method
    // also, we don't have class loader issues when doing so
    // another benefit of not inlining is that the advice methods are included in coverage reports
    @Nullable
    @VisibleForAdvice
    @Advice.OnMethodEnter(inline = false)
    public static Span onBeforeExecute(@Advice.This PreparedStatement statement) throws SQLException {
        if (tracer != null && jdbcEventListener != null) {
            final String sql = ConnectionInstrumentation.getSqlForStatement(statement);
            return jdbcEventListener.createJdbcSpan(sql, statement.getConnection(), tracer.currentSpan());
        }
        return null;
    }


    @VisibleForAdvice
    @Advice.OnMethodExit(inline = false, onThrowable = SQLException.class)
    public static void onAfterExecute(@Advice.Enter @Nullable Span span, @Advice.Thrown SQLException e) {
        if (span != null) {
            span.end();
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        PreparedStatementInstrumentation.tracer = tracer;
        PreparedStatementInstrumentation.jdbcEventListener = new JdbcHelper(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            // pre-select candidates for the more expensive isSubTypeOf matcher
            .and(nameContains("Statement"))
            .and(isSubTypeOf(PreparedStatement.class));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("execute")
            .and(isPublic())
            .and(takesArguments(0));
    }
}
