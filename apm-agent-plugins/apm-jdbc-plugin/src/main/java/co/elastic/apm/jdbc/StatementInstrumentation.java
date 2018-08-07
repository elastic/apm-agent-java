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
import co.elastic.apm.bci.HelperClassManager;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.jdbc.helper.JdbcHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.jdbc.ConnectionInstrumentation.JDBC_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Creates spans for JDBC calls
 */
public class StatementInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<JdbcHelper> jdbcHelper;

    @Nullable
    @VisibleForAdvice
    @Advice.OnMethodEnter
    public static Span onBeforeExecute(@Advice.This Statement statement, @Advice.Argument(0) String sql) throws SQLException {
        if (tracer != null && jdbcHelper != null) {
            return jdbcHelper.getForClassLoaderOfClass(Statement.class).createJdbcSpan(sql, statement.getConnection(), tracer.getActive());
        }
        return null;
    }


    @VisibleForAdvice
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Enter @Nullable Span span, @Advice.Thrown Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        jdbcHelper = HelperClassManager.ForSingleClassLoader.of(tracer, "co.elastic.apm.jdbc.helper.JdbcHelperImpl",
            "co.elastic.apm.jdbc.helper.JdbcHelperImpl$ConnectionMetaData");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            // pre-select candidates for the more expensive hasSuperType matcher
            .and(nameContains("Statement"))
            .and(hasSuperType(named("java.sql.Statement")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("execute")
            .and(isPublic())
            .and(takesArgument(0, String.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(JDBC_INSTRUMENTATION_GROUP);
    }
}
