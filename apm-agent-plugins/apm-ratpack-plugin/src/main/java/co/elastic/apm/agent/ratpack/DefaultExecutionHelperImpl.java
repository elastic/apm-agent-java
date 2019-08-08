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
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Execution;

import java.util.Optional;

@SuppressWarnings("unused")
@IgnoreJRERequirement
public class DefaultExecutionHelperImpl implements DefaultExecutionInstrumentation.DefaultExecutionHelper<Execution, TransactionHolder> {

    // Tracking the scope is thread local as binding and unbinding would happen on the same thread {@see DefaultExecution#drain()}
    // A request's transaction can be active on both a blocking and compute thread at the same time, so we can't
    // track the scope with the Ratpack's Request's context.
    @SuppressWarnings("Convert2Diamond")
    private final ThreadLocal<Scope> currentScope = new ThreadLocal<Scope>();

    private static final Logger logger = LoggerFactory.getLogger(DefaultExecutionHelperImpl.class);

    @Override
    public void activateWhenBound(final Execution execution, final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken) {

        final Optional<TransactionHolder> optional = execution.maybeGet(transactionToken);

        if (optional.isPresent()) {

            final TransactionHolder transactionHolder = optional.get();

            final Transaction transaction = transactionHolder.getTransaction();
            final Transaction currentTransaction = tracer.currentTransaction();

            if (currentTransaction == null && transaction != null) {

                logger.debug("Transaction [{}] is being activated in scope for execution [{}].", transaction, execution);

                final Scope scope = transaction.activateInScope();

                currentScope.set(scope);

            } else if (currentTransaction != null && !currentTransaction.equals(transaction)) {

                logger.warn("Not activating scope for transaction. Current transaction [{}] and stored transaction [{}] are mismatched. This shouldn't happen.", currentTransaction, transaction);

            } else if (currentTransaction != null && currentTransaction.equals(transaction)) {

                logger.debug("Not activating scope for transaction. Current transaction [{}] and stored transaction [{}] are the same.", currentTransaction, transaction);
            }
        }
    }

    @Override
    public void deactivateWhenUnbound(final Execution execution, final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken) {

        final Optional<TransactionHolder> optional = execution.maybeGet(transactionToken);

        if (optional.isPresent()) {

            final TransactionHolder transactionHolder = optional.get();

            final Transaction currentTransaction = tracer.currentTransaction();
            final Transaction transaction = transactionHolder.getTransaction();
            final Scope scope = currentScope.get();

            if (transaction != null) {

                if (scope != null) {

                    logger.debug("Transaction [{}] scope is closing for execution [{}].", transaction, execution);

                    scope.close();

                    currentScope.set(null);

                } else {

                    logger.debug("Not closing scope for transaction. Current transaction [{}] and stored transaction [{}] did not pass assertions.", currentTransaction, transaction);
                }
            }
        }
    }
}
