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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerAnnotatedInstrumentationTest extends AbstractServerInstrumentationTest {

    @Override
    protected GreetingWebClient getClient() {
        return app.getClient(false);
    }

    // only implemented in annotated server version, should not be really different with functional
    @Test
    @Disabled
    // TODO not yet supported, transaction is not seen as active during processing
    void allowCustomTransactionName() {
        Assertions.assertThat(client.executeAndCheckRequest("GET", "/custom-transaction-name", 200))
            .isEqualTo("Hello, transaction!");

        Transaction transaction = getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo("user-provided-name");
    }
}
