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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Matches the various {@link Connection#prepareCall} and {@link Connection#prepareStatement} methods
 * and keeps a reference to from the resulting {@link java.sql.CallableStatement} or {@link PreparedStatement} to the sql.
 */
public class ConnectionInstrumentation extends ElasticApmInstrumentation {

    private static final Map<PreparedStatement, String> statementSqlMap =
        Collections.synchronizedMap(new WeakHashMap<PreparedStatement, String>());

    @VisibleForAdvice
    @Advice.OnMethodExit(inline = false)
    public static void storeSql(@Advice.Return final PreparedStatement statement, @Advice.Argument(0) String sql) {
        statementSqlMap.put(statement, sql);
    }

    /**
     * Returns the SQL statement belonging to provided {@link PreparedStatement}.
     * <p>
     * Might return {@code null} when the provided {@link PreparedStatement} is a wrapper of the actual statement.
     * </p>
     *
     * @return the SQL statement belonging to provided {@link PreparedStatement}, or {@code null}
     */
    @Nullable
    static String getSqlForStatement(PreparedStatement statement) {
        final String sql = statementSqlMap.get(statement);
        if (sql != null) {
            statementSqlMap.remove(statement);
        }
        return sql;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(nameContains("Connection"))
            .and(isSubTypeOf(Connection.class));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("prepare")
            .and(isPublic())
            .and(returns(isSubTypeOf(PreparedStatement.class)))
            .and(takesArgument(0, String.class));
    }

}
