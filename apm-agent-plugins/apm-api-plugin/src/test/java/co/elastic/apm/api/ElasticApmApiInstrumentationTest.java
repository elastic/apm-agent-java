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

import co.elastic.apm.AbstractInstrumentationTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmApiInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testCreateTransaction() {
        assertThat(ElasticApm.startTransaction()).isNotSameAs(NoopTransaction.INSTANCE);
        assertThat(ElasticApm.currentTransaction()).isNotSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testCreateAsyncTransaction() {
        assertThat(ElasticApm.startAsyncTransaction()).isNotSameAs(NoopTransaction.INSTANCE);
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testNoCurrentTransaction() {
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testCreateSpan() {
        assertThat(ElasticApm.startSpan()).isNotSameAs(NoopSpan.INSTANCE);
        assertThat(ElasticApm.currentSpan()).isNotSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testNoCurrentSpan() {
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testCaptureException() {
        ElasticApm.captureException(new RuntimeException("Bazinga"));
        assertThat(reporter.getErrors()).hasSize(1);
    }
}
