/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Collections;

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
 * Matches the various {@link Connection#prepareCall} and {@link Connection#prepareStatement} methods
 * and keeps a reference to from the resulting {@link java.sql.CallableStatement} or {@link PreparedStatement} to the sql.
 */
public class ConnectionInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final WeakConcurrentMap<Object, String> statementSqlMap = new WeakConcurrentMap<Object, String>(true);
    static final String JDBC_INSTRUMENTATION_GROUP = "jdbc";

    @VisibleForAdvice
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void storeSql(@Advice.Return final PreparedStatement statement, @Advice.Argument(0) String sql) {
        statementSqlMap.putIfAbsent(statement, sql);
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
    @VisibleForAdvice
    public static String getSqlForStatement(Object statement) {
        return statementSqlMap.get(statement);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Connection");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("java.sql.Connection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("prepare")
            .and(returns(hasSuperType(named("java.sql.PreparedStatement"))))
            .and(takesArgument(0, String.class))
            .and(isPublic());
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(JDBC_INSTRUMENTATION_GROUP);
    }

}
