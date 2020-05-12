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
package co.elastic.apm.agent.rocketmq.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.List;

public interface RocketMQInstrumentationHelper<M, MQ, CM, SC, MLC, MLO, PR, PC, ME> {

    @Nullable
    Span onSendStart(M msg, MQ mq, CM communicationMode);

    @Nullable
    SC wrapSendCallback(@Nullable SC delegate, Span span);

    @Nullable
    MLC wrapMessageListenerConcurrently(@Nullable MLC listenerConcurrently);

    @Nullable
    MLO wrapMessageListenerOrderly(@Nullable MLO listenerOrderly);

    @Nullable
    PR replaceMsgList(@Nullable PR delegate);

    @Nullable
    PC wrapPullCallback(@Nullable PC delegate);

    List<ME> wrapMessageList(List<ME> msgs);

    @Nullable
    Transaction onConsumeStart(ME msg);

    void onConsumeEnd(Transaction transaction, Throwable throwable, Object ret);

}
