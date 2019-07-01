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
package co.elastic.apm.agent.hibernate.search;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

@VisibleForAdvice
public final class HibernateSearchHelper {

    private HibernateSearchHelper() {

    }

    @VisibleForAdvice
    public static Span createAndActivateSpan(final ElasticApmTracer tracer, final String query) {
        Span span = null;

        if (tracer != null) {
            TraceContextHolder<?> active = tracer.getActive();
            if (active == null || active instanceof Span && HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE
                .equals(((Span) active).getSubtype())) {
                return null;
            }

            span = active.createSpan().activate();

            span.withType("db")
                .withSubtype(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE)
                .withAction(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_ACTION);
            span.getContext().getDb()
                .withType(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE)
                .withStatement(query);
            span.setName(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_SPAN_NAME);
        }
        return span;
    }
}
