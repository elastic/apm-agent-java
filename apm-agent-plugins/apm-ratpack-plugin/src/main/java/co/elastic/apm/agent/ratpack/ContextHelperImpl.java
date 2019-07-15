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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;

import java.util.Optional;

@SuppressWarnings("unused")
@IgnoreJRERequirement
public class ContextHelperImpl implements ContextInstrumentation.ContextHelper<Context, TransactionHolder> {

    private static final Logger logger = LoggerFactory.getLogger(ContextHelperImpl.class);

    @Override
    public void captureException(final Context context, final Throwable throwable, final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken) {

        final Optional<TransactionHolder> optional = context.maybeGet(transactionToken);

        if (optional.isPresent()) {

            final TransactionHolder transactionHolder = optional.get();

            final Transaction currentTransaction = tracer.currentTransaction();
            final Transaction transaction = transactionHolder.getTransaction();

            if (transaction != null) {

                logger.debug("Transaction [{}] is capturing exception [{}].", transaction, throwable);

                transaction.captureException(throwable);
            } else {

                //noinspection ConstantConditions
                logger.debug("Not capturing exception for transaction. Current transaction [{}] and stored transaction [{}] did not pass assertions.", currentTransaction, transaction);
            }
        }
    }
}
