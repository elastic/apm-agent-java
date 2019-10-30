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
package co.elastic.apm.agent.neo4j3;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.DataStructures;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class TransactionInstrumentation extends ElasticApmInstrumentation {

  @VisibleForAdvice
  public static final WeakConcurrentMap<org.neo4j.driver.v1.Transaction, Span> inFlightSpans = DataStructures.createWeakConcurrentMapWithCleanerThread();

  @Override
  public ElementMatcher<? super TypeDescription> getTypeMatcher() {
    return not(isInterface())
        .and(hasSuperType(named("org.neo4j.driver.v1.Transaction")));
  }

  @Override
  public Collection<String> getInstrumentationGroupNames() {
    return Arrays.asList("neo4j", "neo4j3");
  }

  public static class CreateSpanInstrumentation extends TransactionInstrumentation {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.This org.neo4j.driver.v1.Transaction thiz,
        @Advice.Local("span") Span span,
        @Advice.Argument(0) org.neo4j.driver.v1.Statement statement) {

      if (tracer == null || tracer.getActive() == null) {
        return;
      }

      if (statement == null) {
        return;
      }

      final TraceContextHolder<?> parent = tracer.getActive();

      span = parent.createExitSpan();
      if (span != null) {
        String cypher = statement.text();
        String cypherKeyword = cypher.substring(0, cypher.indexOf(" "));
        span.withType("db")
            .withSubtype("neo4j3")
            .withAction("query")
            .withName(cypherKeyword);
        span.getContext().getDb()
            .withType("cypher")
            .withStatement(cypher);
        span.activate();
        inFlightSpans.put(thiz, span);
      }
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
      return named("run")
          .and(takesArgument(0, named("org.neo4j.driver.v1.Statement")))
          .and(returns(named("org.neo4j.driver.v1.StatementResult")));
    }

  }

  public static class FinishSpanInstrumentation extends TransactionInstrumentation {

    @VisibleForAdvice
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.This org.neo4j.driver.v1.Transaction thiz,
        @Advice.Thrown @Nullable Throwable t) {
      Span span = inFlightSpans.get(thiz);
      if (span != null) {
        try {
          span.captureException(t);
        } finally {
          span.deactivate().end();
          inFlightSpans.remove(thiz);
        }
      }
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
      return takesArguments(0)
        .and(named("close").or(named("success")).or(named("failure")));
    }

  }

}
