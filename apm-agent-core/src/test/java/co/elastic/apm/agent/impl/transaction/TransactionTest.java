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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionTest {

    private DslJsonSerializer jsonSerializer;

    @BeforeEach
    void setUp() {
        jsonSerializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
    }

    @Test
    void resetState() {
        final Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        TransactionUtils.fillTransaction(transaction);
        transaction.resetState();
        assertThat(jsonSerializer.toJsonString(transaction)).isEqualTo(jsonSerializer.toJsonString(new Transaction(mock(ElasticApmTracer.class))));
    }
}
