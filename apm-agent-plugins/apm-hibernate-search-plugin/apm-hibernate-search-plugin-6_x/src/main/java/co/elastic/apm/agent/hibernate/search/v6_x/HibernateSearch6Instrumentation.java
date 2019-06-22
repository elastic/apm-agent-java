package co.elastic.apm.agent.hibernate.search.v6_x;

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
import org.hibernate.search.backend.lucene.search.query.impl.LuceneSearchQueryImpl;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class HibernateSearch6Instrumentation extends ElasticApmInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return Hibernate6ExecuteAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.hibernate.search.backend.lucene.search.query.impl.LuceneSearchQueryImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fetch");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(HibernateSearchConstants.HIBERNATE_SEARCH_ORM_TYPE);
    }

    @VisibleForAdvice
    public static class Hibernate6ExecuteAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.This LuceneSearchQueryImpl query,
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
