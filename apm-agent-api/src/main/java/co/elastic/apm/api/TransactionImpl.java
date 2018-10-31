/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.api;

import javax.annotation.Nonnull;

/**
 * If the agent is active, it injects the implementation from
 * co.elastic.apm.plugin.api.TransactionInstrumentation
 * into this class.
 * <p>
 * Otherwise, this class is a noop.
 * </p>
 */
class TransactionImpl extends SpanImpl implements Transaction {

    TransactionImpl(@Nonnull Object transaction) {
        super(transaction);
    }

    @Override
    public void setUser(String id, String email, String username) {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$SetUserInstrumentation.setUser
    }

    @Nonnull
    @Override
    public String makeChildOfRumTransaction() {
        // co.elastic.apm.plugin.api.TransactionInstrumentation.MakeChildOfRumTransactionInstrumentation
        return "";
    }

}
