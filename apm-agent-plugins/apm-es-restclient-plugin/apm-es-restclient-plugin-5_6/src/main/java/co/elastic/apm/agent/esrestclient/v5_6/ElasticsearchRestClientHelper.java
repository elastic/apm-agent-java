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
package co.elastic.apm.agent.esrestclient.v5_6;

import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import org.apache.http.Header;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class ElasticsearchRestClientHelper extends ElasticsearchRestClientInstrumentationHelper {

    private static final ElasticsearchRestClientHelper INSTANCE = new ElasticsearchRestClientHelper(GlobalTracer.requireTracerImpl());

    public static ElasticsearchRestClientHelper get() {
        return INSTANCE;
    }

    protected ElasticsearchRestClientHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public void captureClusterName(final RestClient restClient, @Nullable Span span, final Header[] headers) {
        if (startGetClusterName(restClient, span)) {
            endGetClusterName(restClient, new Callable<InputStream>() {
                @Override
                public InputStream call() throws Exception {
                    Response response = restClient.performRequest("GET", "/_nodes", headers);
                    return response.getStatusLine().getStatusCode() == 200 ? response.getEntity().getContent() : null;
                }
            });
        }
    }
}
