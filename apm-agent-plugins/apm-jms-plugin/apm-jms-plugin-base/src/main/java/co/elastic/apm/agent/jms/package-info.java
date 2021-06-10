/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
 * JMS tracing includes two parts in general:
 * 1. Visibility to JMS events like message sends and receive, including relevant metadata
 * 2. Distributed tracing
 * <p>
 * Capturing of sending event is trivial- can only be traced as a span within a traced transaction.
 * Receive events are a bit trickier. Message receive can be passive (i.e. listening, for example through the
 * {@link javax.jms.MessageListener#onMessage(javax.jms.Message)} API) or active (i.e. polling, for example through the
 * {@link javax.jms.MessageConsumer#receive()} API).
 * <p>
 * Passive listening is easy to trace, as the API is natural for this purpose- its start marks the interesting start
 * event, where the Message is provided with required metadata, and its end marks the interesting end event.
 * <p>
 * On the other hand, polling APIs are non-trivial for tracing. Their invocation event is arbitrary (meaning their
 * duration is not of interest) and we really want to trace what happens AFTER they exit with a Message. We try to deal
 * with that by assuming that the same thread invoking the polling API will either execute the Message handling logic or
 * delegate to another thread to do that and we can propagate our tracing context along with this delegation.
 * <p>
 * For both send and receive events, we capture and trace the destination name.
 * For receive transactions, we capture message body (only for text messages, configurable through `capture_body`, off
 * by default) and headers/properties as well (configurable through `capture_headers`, on by default).
 * <p>
 * There is a special treatment for temporary queues/topic, which are short lived and do not represent consistent
 * entities, as opposed to non-temp ones. As such, they typically get arbitrary non-meaningful names, which we want
 * to hide so to avoid name explosion.
 */
@NonnullApi
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.sdk.NonnullApi;
