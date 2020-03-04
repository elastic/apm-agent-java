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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import com.squareup.okhttp.Request;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractOkHttpClientInstrumentation extends ElasticApmInstrumentation {

    // We can refer OkHttp types thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<Request.Builder>> headerSetterHelperManager;

    public AbstractOkHttpClientInstrumentation(ElasticApmTracer tracer) {
        synchronized (AbstractOkHttpClientInstrumentation.class) {
            if (headerSetterHelperManager == null) {
                headerSetterHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.okhttp.OkHttpRequestHeaderSetter"
                );
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "okhttp");
    }
}
