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
package co.elastic.apm.agent.jms.jakarta.test;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.GlobalTracer;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.util.concurrent.atomic.AtomicReference;

public class TestMessageListener implements MessageListener {

    private final AtomicReference<Transaction> transaction;

    public TestMessageListener(AtomicReference<Transaction> transaction) {
        this.transaction = transaction;
    }

    @Override
    public void onMessage(Message message) {
        transaction.set(GlobalTracer.get().require(Tracer.class).currentTransaction());
    }
}
