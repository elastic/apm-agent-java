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

/**
 * Kafka tracing includes two parts in general:
 * 1. Visibility to record production and consumption events, including relevant metadata
 * 2. Distributed tracing
 * <p>
 * Capturing of record production events is trivial- can only be traced as a span within a traced transaction.
 * Consumption is a bit trickier as it relies on the {@link org.apache.kafka.clients.consumer.KafkaConsumer#poll}
 * API. The challenge with polling APIs is that their invocation event is typically arbitrary (meaning their
 * duration is not of interest) and we really want to trace what happens AFTER they exit with a Message. Since Kafka
 * records are only consumed in {@link java.lang.Iterable} batches, we try to trace the actions executed between
 * iterations by starting/activating and ending/deactivating a transaction for each record during iteration.
 * Such transactions will have the {@code messaging} type. This may result in non-desirable traces, for example if the
 * iterator is held and not immediately iterated all through, but we assume this is somewhat of an edge case.
 * As long as we can make it safe from memory leaks, this is acceptable.
 * In case the polling is executed from within a traced transaction, we won't create a transaction-per-record. Instead,
 * we will trace the polling action itself and create a span for it.
 * <p>
 * For both produce and consume events, we capture and trace the topic name.
 * For kafka transactions, we capture record value (as long as the defined deserializer outputs text; configurable
 * through `capture_body`, off by default) and record headers as well (if they are UTF-8 encoded; configurable through
 * `capture_headers`, on by default).
 * <p>
 */
@NonnullApi
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.sdk.NonnullApi;
