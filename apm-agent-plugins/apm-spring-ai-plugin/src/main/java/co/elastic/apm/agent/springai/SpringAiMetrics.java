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
package co.elastic.apm.agent.springai;

import co.elastic.apm.agent.embeddedotel.EmbeddedSdkManager;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributeKey;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributes;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributesBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleHistogram;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;

import javax.annotation.Nullable;

final class SpringAiMetrics {

    static final String TIME_TO_FIRST_CHUNK = "gen_ai.client.inference.time_to_first_chunk";

    private static final Tracer tracer = GlobalTracer.get();
    private static final ProxyAttributeKey<String> OPERATION_NAME =
        ProxyAttributeKey.stringKey("gen_ai.operation.name");
    private static final ProxyAttributeKey<String> PROVIDER_NAME =
        ProxyAttributeKey.stringKey("gen_ai.provider.name");
    private static final ProxyAttributeKey<String> REQUEST_MODEL =
        ProxyAttributeKey.stringKey("gen_ai.request.model");
    @Nullable
    private static final ProxyDoubleHistogram timeToFirstChunk = createTimeToFirstChunkHistogram();

    private SpringAiMetrics() {
    }

    static void recordTimeToFirstChunk(String provider, @Nullable String requestModel, double seconds) {
        if (timeToFirstChunk == null) {
            return;
        }
        timeToFirstChunk.record(seconds, createAttributes(provider, requestModel));
    }

    private static ProxyAttributes createAttributes(String provider, @Nullable String requestModel) {
        ProxyAttributesBuilder builder = ProxyAttributes.builder();
        builder.put(OPERATION_NAME, "chat");
        builder.put(PROVIDER_NAME, provider);
        if (requestModel != null) {
            builder.put(REQUEST_MODEL, requestModel);
        }
        return builder.build();
    }

    @Nullable
    private static ProxyDoubleHistogram createTimeToFirstChunkHistogram() {
        EmbeddedSdkManager sdkManager = tracer.require(ElasticApmTracer.class)
            .getLifecycleListener(EmbeddedSdkManager.class);
        if (sdkManager == null) {
            return null;
        }
        return sdkManager.getMeterProvider()
            .get("co.elastic.apm.spring-ai")
            .histogramBuilder(TIME_TO_FIRST_CHUNK)
            .setUnit("s")
            .setDescription("Time to receive the first chunk of a streaming generative AI response")
            .build();
    }
}
