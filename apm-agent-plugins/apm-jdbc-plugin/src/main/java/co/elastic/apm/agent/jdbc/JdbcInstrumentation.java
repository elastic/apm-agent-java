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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.jdbc.helper.JdbcHelper;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public abstract class JdbcInstrumentation extends ElasticApmInstrumentation {

    private static final Collection<String> JDBC_GROUPS = Collections.singleton("jdbc");

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<JdbcHelper> jdbcHelperManager = null;

    public JdbcInstrumentation(ElasticApmTracer tracer) {
        synchronized (JdbcInstrumentation.class) {
            if (jdbcHelperManager == null) {
                jdbcHelperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                    "co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl",
                    "co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl$1");
            }
        }
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return JDBC_GROUPS;
    }

}
