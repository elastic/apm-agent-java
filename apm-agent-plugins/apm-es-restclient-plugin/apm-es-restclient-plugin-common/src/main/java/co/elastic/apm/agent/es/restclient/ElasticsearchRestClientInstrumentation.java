/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.es.restclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;


public abstract class ElasticsearchRestClientInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    // Referencing ES classes is legal due to type erasure. The field must be public in order for it to be accessible from injected code
    public static HelperClassManager<ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener>> esClientInstrHelperManager;

    @Override
    public void init(ElasticApmTracer tracer) {
        esClientInstrHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl",
            "co.elastic.apm.agent.es.restclient.ResponseListenerWrapper",
            "co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelperImpl$ResponseListenerAllocator");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("elasticsearch-restclient");
    }
}
