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
package co.elastic.apm.agent.websocket;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.websocket.endpoint.WebSocketEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

abstract class BaseServerEndpointInstrumentationTest extends AbstractInstrumentationTest {

    private final WebSocketEndpoint serverEndpoint;

    protected BaseServerEndpointInstrumentationTest(WebSocketEndpoint serverEndpoint) {
        this.serverEndpoint = serverEndpoint;
    }

    @Test
    void testOnOpenWithActiveTransaction() {
        Transaction transaction = startTestRootTransaction();
        try {
            serverEndpoint.onOpen();
        } finally {
            transaction.deactivate().end();
        }

        assertReportedTransactionNameAndFramework("onOpen");
    }

    @Test
    void testOnOpenWithoutActiveTransaction() {
        serverEndpoint.onOpen();

        assertReportedTransactionNameAndFramework("onOpen");
    }

    @Test
    void testOnMessage() {
        Transaction transaction = startTestRootTransaction();
        try {
            serverEndpoint.onMessage("");
        } finally {
            transaction.deactivate().end();
        }

        assertReportedTransactionNameAndFramework("onMessage");
    }

    @Test
    void testOnError() {
        Transaction transaction = startTestRootTransaction();
        try {
            serverEndpoint.onError();
        } finally {
            transaction.deactivate().end();
        }

        assertReportedTransactionNameAndFramework("onError");
    }

    @Test
    void testOnClose() {
        Transaction transaction = startTestRootTransaction();
        try {
            serverEndpoint.onClose();
        } finally {
            transaction.deactivate().end();
        }

        assertReportedTransactionNameAndFramework("onClose");
    }

    protected abstract String getWebSocketServerEndpointClassName();

    protected abstract String getFrameworkName();

    protected abstract String getFrameworkVersion();

    private void assertReportedTransactionNameAndFramework(String methodName) {
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(getWebSocketServerEndpointClassName() + '#' + methodName);
        assertThat(transaction.getFrameworkName()).isEqualTo(getFrameworkName());
        assertThat(transaction.getFrameworkVersion()).isEqualTo(getFrameworkVersion());
    }
}
