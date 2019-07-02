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
import co.elastic.apm.agent.impl.transaction.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class HibernateSearchAssertionHelper {

    private HibernateSearchAssertionHelper() {

    }

    public static void assertApmSpanInformation(final MockReporter reporter, final String expectedQuery, final String searchMethod) {
        assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
        final Span span = reporter.getFirstSpan();
        assertEquals(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE, span.getSubtype(), "Subtype of span is not 'hibernate-search'");
        assertEquals(expectedQuery, span.getContext().getDb().getStatement(),"Statement is not '" + expectedQuery + "'");
        assertEquals("db", span.getType());
        assertEquals(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_ACTION, span.getAction());
        assertEquals(HibernateSearchHelper.buildSpanName(searchMethod), span.getName().toString());
    }
}
