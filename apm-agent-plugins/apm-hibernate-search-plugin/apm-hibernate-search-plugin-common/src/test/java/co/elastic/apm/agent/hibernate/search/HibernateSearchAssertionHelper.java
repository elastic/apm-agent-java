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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;

import static org.assertj.core.api.Assertions.assertThat;

public final class HibernateSearchAssertionHelper {

    private HibernateSearchAssertionHelper() {

    }

    public static void assertApmSpanInformation(final MockReporter reporter, final String expectedQuery, final String searchMethod) {
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        final Span span = reporter.getFirstSpan();
        assertThat(span.getSubtype()).isEqualTo(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE);
        assertThat(span.getContext().getDb().getStatement()).isEqualTo(expectedQuery);
        assertThat(span.getType()).isEqualTo(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_SPAN_TYPE);
        assertThat(span.getAction()).isEqualTo(searchMethod);
        assertThat(span.getNameAsString()).isEqualTo(buildSpanName(searchMethod));
        Destination.Service service = span.getContext().getDestination().getService();
        assertThat(service.getName().toString()).isEqualTo(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE);
        assertThat(service.getResource().toString()).isEqualTo(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE);
        assertThat(service.getType()).isEqualTo(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_SPAN_TYPE);
    }

    private static String buildSpanName(final String methodName) {
        return HibernateSearchConstants.HIBERNATE_SEARCH_ORM_SPAN_NAME + " " + methodName + "()";
    }
}
