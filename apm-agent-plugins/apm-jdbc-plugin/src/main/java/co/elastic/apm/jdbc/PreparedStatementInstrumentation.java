/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.jdbc.helper.JdbcHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static co.elastic.apm.jdbc.ConnectionInstrumentation.JDBC_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class PreparedStatementInstrumentation extends ElasticApmInstrumentation {


    // not inlining as we can then set breakpoints into this method
    // also, we don't have class loader issues when doing so
    // another benefit of not inlining is that the advice methods are included in coverage reports
    @Nullable
    @VisibleForAdvice
    @Advice.OnMethodEnter
    public static Span onBeforeExecute(@Advice.This PreparedStatement statement) throws SQLException {
        if (tracer != null && helperClassManager != null) {
            final String sql = ConnectionInstrumentation.getSqlForStatement(statement);
            return helperClassManager.getHelperClass(Statement.class.getClassLoader(), JdbcHelper.class)
                .createJdbcSpan(sql, statement.getConnection(), tracer.getActive());
        }
        return null;
    }

    @VisibleForAdvice
    @Advice.OnMethodExit(onThrowable = SQLException.class)
    public static void onAfterExecute(@Advice.Enter @Nullable Span span, @Advice.Thrown SQLException e) {
        if (span != null) {
            span.deactivate().end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            // pre-select candidates for the more expensive hasSuperType matcher
            .and(nameContains("Statement"))
            .and(hasSuperType(named("java.sql.PreparedStatement")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("execute")
            .and(isPublic())
            .and(takesArguments(0));
    }

    @Override
    public String getInstrumentationGroupName() {
        return JDBC_INSTRUMENTATION_GROUP;
    }
}
