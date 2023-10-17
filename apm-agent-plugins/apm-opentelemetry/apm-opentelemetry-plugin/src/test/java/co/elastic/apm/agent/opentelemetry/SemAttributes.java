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
 * Bridge for integration tests which use {@link io.opentelemetry.semconv.SemanticAttributes}
 * which has been moved from {@code io.opentelemetry.semconv.trace.attributes.SemanticAttributes}.
 */
public class SemAttributes {

    public static final AttributeKey<String> HTTP_URL = getAttribute("HTTP_URL");
    public static final AttributeKey<Long> HTTP_STATUS_CODE = getAttribute("HTTP_STATUS_CODE");
    public static final AttributeKey<String> HTTP_METHOD = getAttribute("HTTP_METHOD");
    public static final AttributeKey<Long> NET_PEER_PORT = getAttribute("NET_PEER_PORT");
    public static final AttributeKey<String> NET_PEER_IP = getAttribute("NET_PEER_IP");

    @SuppressWarnings("unchecked")
    private static <T> AttributeKey<T> getAttribute(String name) {
        try {
            Class<?> attribClass;
            try {
                attribClass = Class.forName("io.opentelemetry.semconv.SemanticAttributes");
            } catch (ClassNotFoundException cnf) {
                attribClass = Class.forName("io.opentelemetry.semconv.trace.attributes.SemanticAttributes");
            }
            return (AttributeKey<T>) attribClass.getField(name).get(null);
        }catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
