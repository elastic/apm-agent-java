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
package co.elastic.apm.agent.opentelemetry.tracing;

/**
 * An extension to the OTel API that allows us to provide special tracing settings.
 * Such settings may affect tracing behavior internally, but they are not added as data and not persisted as span attributes.
 */
public class BehavioralAttributes {

    /**
     * By default, spans may be discarded. For example if {@code span_min_duration} config option is set and the span does not exceed
     * the configured threshold.
     * Use this attribute to make the span non-discardable by setting it to {@code false}.
     *
     * NOTE: making a span non-discardable implicitly makes the entire stack of active spans non-discardable as well. Child spans can
     * still be discarded.
     */
    public static final String DISCARDABLE = "co.elastic.discardable";
}
