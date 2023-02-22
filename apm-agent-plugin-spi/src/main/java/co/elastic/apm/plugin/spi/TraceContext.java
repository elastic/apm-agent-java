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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface TraceContext {

    String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    String W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME = "traceparent";
    String TRACESTATE_HEADER_NAME = "tracestate";
    public static final int SERIALIZED_LENGTH = 42;

    String TRACE_PARENT_BINARY_HEADER_NAME = "elasticapmtraceparent";
    int BINARY_FORMAT_EXPECTED_LENGTH = 29;

    Id getTraceId();

    Id getId();

    Id getParentId();

    Id getTransactionId();

    void setServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion);

    boolean asChildOf(byte[] parent);

    boolean asChildOf(String parent);

    void asChildOf(TraceContext parent);

    void addTraceState(String header);
}
