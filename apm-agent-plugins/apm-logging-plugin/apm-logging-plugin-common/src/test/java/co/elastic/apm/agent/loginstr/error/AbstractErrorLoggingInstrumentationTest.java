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
package co.elastic.apm.agent.loginstr.error;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.Assert.assertEquals;

public abstract class AbstractErrorLoggingInstrumentationTest extends AbstractInstrumentationTest {

    private Transaction transaction;

    @BeforeEach
    void startTransaction() {
        transaction = tracer.startRootTransaction(null);
        transaction.activate();
    }

    @AfterEach
    void endTransaction() {
        transaction.deactivate().end();
    }

    protected void verifyThatExceptionCaptured(int errorCount, String exceptionMessage, Class exceptionClass) {
        reporter.awaitErrorCount(1);
        Throwable exception = reporter.getErrors().get(0).getException();
        assertEquals(exceptionMessage, exception.getMessage());
        assertEquals(exceptionClass, exception.getClass());
    }
}
