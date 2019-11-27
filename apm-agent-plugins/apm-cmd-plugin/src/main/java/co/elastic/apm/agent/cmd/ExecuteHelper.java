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
package co.elastic.apm.agent.cmd;

import javax.annotation.Nullable;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

@Deprecated
@VisibleForAdvice
public class ExecuteHelper {

    @VisibleForAdvice
    public static Span createAndActivateSpan(final ElasticApmTracer tracer, final String binaryName,
                                             final String[] binaryArguments, final String subType) {
        if (tracer == null || tracer.getActive() == null) {
            return null;
        }

        TraceContextHolder<?> active = tracer.getActive();
        // avoid creating multiple spans for wrapped APIs
        if (active instanceof Span && subType.equals(((Span) active).getSubtype())) {
            return null;
        }

        final Span span = active.createSpan().activate();

        span.withType("execute")
            .withSubtype(subType)
            .withAction("execute");
        // TODO: Find a place to add the binary arguments



        span.withName("Execute ").appendToName(binaryName);

        return span;
    }

    @VisibleForAdvice
    public static void endAndDeactivateSpan(final Span span, final Throwable t, @Nullable final Integer exitValue) {
        // TODO: Capture exit code of process
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
