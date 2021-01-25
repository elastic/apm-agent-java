/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.opentelemetry.sdk;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import javax.annotation.Nullable;
import java.util.Collection;

public class ElasticOTelTextMapPropagator implements TextMapPropagator {

    private static final TextMapPropagator W3C_PROPAGATOR = W3CTraceContextPropagator.getInstance();

    @Override
    public Collection<String> fields() {
        return W3C_PROPAGATOR.fields();
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, Setter<C> setter) {
        Span span = Span.fromContext(context);
        if (span instanceof ElasticOTelSpan) {
            ((ElasticOTelSpan) span).getInternalSpan().setNonDiscardable();
        }
        W3C_PROPAGATOR.inject(context, carrier, setter);
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, Getter<C> getter) {
        return W3C_PROPAGATOR.extract(context, carrier, getter);
    }
}
