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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.jdbc.helper.JdbcHelper;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class JdbcInstrumentation extends TracerAwareInstrumentation {

    private static final Collection<String> JDBC_GROUPS = Collections.singleton("jdbc");

    @Nullable
    private static JdbcHelper jdbcHelper;

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("java.sql.Statement"); // in case java.sql module is not there
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return JDBC_GROUPS;
    }

    protected synchronized static JdbcHelper getJdbcHelper() {
        // lazily initialize helper to prevent trying to load classes in java.sql package with the bootstrap classloader
        // this method should only be called from advices
        if (jdbcHelper == null) {
            jdbcHelper = new JdbcHelper();
        }
        return jdbcHelper;
    }
}
