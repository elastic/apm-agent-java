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
package co.elastic.apm.agent.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Bridge for integration tests which use semconv attributes
 * which has been moved from {@code io.opentelemetry.semconv.trace.attributes.SemanticAttributes}.
 */
public class SemAttributes {

    public static final AttributeKey<String> HTTP_URL = AttributeKey.stringKey("HTTP_URL");
    public static final AttributeKey<Long> HTTP_STATUS_CODE = AttributeKey.longKey("HTTP_STATUS_CODE");
    public static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("HTTP_METHOD");
    public static final AttributeKey<Long> NET_PEER_PORT = AttributeKey.longKey("NET_PEER_PORT");
    public static final AttributeKey<String> NET_PEER_IP = AttributeKey.stringKey("NET_PEER_IP");
}
