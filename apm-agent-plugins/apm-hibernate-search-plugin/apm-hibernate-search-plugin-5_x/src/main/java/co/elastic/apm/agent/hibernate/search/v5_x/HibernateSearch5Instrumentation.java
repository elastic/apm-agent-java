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
package co.elastic.apm.agent.hibernate.search.v5_x;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.hibernate.search.HibernateSearchConstants;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.search.query.hibernate.impl.FullTextQueryImpl;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class HibernateSearch5Instrumentation extends ElasticApmInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return HibernateSearch5ExecuteAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("org.hibernate.search.FullTextQuery")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("list")
            .or(named("scroll"))
            .or(named("iterate"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE);
    }

    @VisibleForAdvice
    public static class HibernateSearch5ExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.This FullTextQueryImpl query,
            @Advice.Local("span") Span span) {
            if (tracer != null) {
                TraceContextHolder<?> active = tracer.getActive();
                if (active == null || active instanceof Span && HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE
                    .equals(((Span) active).getSubtype())) {
                    return;
                }

                final @Nullable String queryString = query.getQueryString();
                span = active.createSpan().activate();

                span.withType("db")
                    .withSubtype(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE)
                    .withAction("request");
                span.getContext().getDb()
                    .withType(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE)
                    .withStatement(queryString);
                span.setName(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_SPAN_NAME);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
            @Advice.Thrown Throwable t) {
            if (span != null) {
                try {
                    span.captureException(t);
                } finally {
                    span.end();
                    span.deactivate();
                }
            }
        }
    }
}
