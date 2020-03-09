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
package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.Assert.assertEquals;

abstract class AbstractErrorLoggingInstrumentationTest extends AbstractInstrumentationTest {

    @BeforeEach
    void startTransaction() {
        reporter.reset();
        tracer.startRootTransaction(null).activate();
    }

    @AfterEach
    void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.reset();
    }

    void verifyThatExceptionCaptured(int errorCount, String exceptionMessage, Class exceptionClass) {
        assertEquals(errorCount, reporter.getErrors().size());
        Throwable exception = reporter.getErrors().get(0).getException();
        assertEquals(exceptionMessage, exception.getMessage());
        assertEquals(exceptionClass, exception.getClass());
    }
}
