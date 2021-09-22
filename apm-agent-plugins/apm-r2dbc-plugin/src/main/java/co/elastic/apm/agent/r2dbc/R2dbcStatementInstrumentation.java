
/*
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
 */
package co.elastic.apm.agent.r2dbc;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.r2dbc.helper.R2dbcHelper;
import co.elastic.apm.agent.r2dbc.helper.R2dbcReactorHelper;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Creates spans for R2DBC {@link Statement} execution
 */
public class R2dbcStatementInstrumentation extends AbstractR2dbcInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(R2dbcStatementInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Statement");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("io.r2dbc.spi.Statement")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesNoArguments())
            .and(isPublic());
    }

    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.This Object statementObject) {
            R2dbcHelper helper = R2dbcHelper.get();
            logger.info("Trying to handle with statement = {} on thread = {}", statementObject, Thread.currentThread().getName());
            Object[] connectionSqlObj = helper.retrieveConnectionForStatement(statementObject);
            Object connectionObj = connectionSqlObj != null ? connectionSqlObj[0] : null;
            Object sqlObject = connectionSqlObj != null ? connectionSqlObj[1] : null;
            @Nullable String sql = sqlObject instanceof String ? (String) sqlObject : null;
            @Nullable Connection connection = connectionObj instanceof Connection ? (Connection) connectionObj : null;

            AbstractSpan<?> parent = tracer.getActive();
            return R2dbcHelper.get().createR2dbcSpan(connection, sql, parent);
        }


        @Nullable
        @AssignTo.Return(typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object onAfterExecute(@Advice.Enter @Nullable Object spanObject,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.Return @Nullable Publisher<? extends Result> returnValue) {
            if (!(spanObject instanceof Span)) {
                return returnValue;
            }
            Span span = (Span) spanObject;
            span = span.captureException(t).deactivate();
            if (t != null || returnValue == null) {
                return returnValue;
            }
            return R2dbcReactorHelper.wrapPublisher(tracer, returnValue, span);
        }
    }

// TODO Connection#createBatch
// TODO Batch#execute

}
