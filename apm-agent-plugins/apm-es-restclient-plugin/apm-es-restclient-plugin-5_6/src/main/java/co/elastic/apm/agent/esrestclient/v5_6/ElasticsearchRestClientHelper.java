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
import org.apache.http.Header;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;

public class ElasticsearchRestClientHelper extends ElasticsearchRestClientInstrumentationHelper {

    private static final ElasticsearchRestClientHelper INSTANCE = new ElasticsearchRestClientHelper(GlobalTracer.requireTracerImpl());

    public static ElasticsearchRestClientHelper get() {
        return INSTANCE;
    }

    private ElasticsearchRestClientHelper(ElasticApmTracer tracer) {
        super(tracer, new HttpClientAdapter() {

            @Override
            public void performRequestAsync(RestClient restClient, String method, String path, Object headersOrRequestOptions, ResponseListener responseListener) {
                if (!(headersOrRequestOptions instanceof Header[])) {
                    throw new IllegalArgumentException("unexpected header type");
                }
                Header[] headers = (Header[]) headersOrRequestOptions;
                restClient.performRequestAsync(method, path, responseListener, headers);
            }
        });
    }

}
