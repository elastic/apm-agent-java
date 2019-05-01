/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.Recyclable;

import java.util.ArrayList;
import java.util.List;


/**
 * Transactions payload
 * <p>
 * List of transactions wrapped in an object containing some other attributes normalized away from the transactions themselves
 */
public class TransactionPayload extends Payload {

    /**
     * (Required)
     */
    private final List<Transaction> transactions = new ArrayList<Transaction>();

    private final List<Span> spans = new ArrayList<Span>();

    public TransactionPayload(ProcessInfo process, Service service, SystemInfo system) {
        super(process, service, system);
    }

    /**
     * (Required)
     */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public void resetState() {
        transactions.clear();
        spans.clear();
    }

    @Override
    public List<? extends Recyclable> getPayloadObjects() {
        return transactions;
    }

    @Override
    public int getPayloadSize() {
        return transactions.size() + spans.size();
    }

    @Override
    public void recycle() {
        for (int i = 0; i < transactions.size(); i++) {
            transactions.get(i).recycle();
        }
        for (int i = 0; i < spans.size(); i++) {
            spans.get(i).recycle();
        }
    }
}
