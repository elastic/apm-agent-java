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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationApiTest extends AbstractInstrumentationTest {

    @Test
    void testCaptureTransactionAnnotation() {
        new AnnotationTestClass().transaction();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("transaction");
        assertThat(reporter.getFirstTransaction().getContext().getTags()).containsEntry("foo", "bar");

        assertThat(reporter.getSpans()).hasSize(2);

        assertThat(reporter.getSpans().get(0).getName().toString()).isEqualTo("AnnotationTestClass#nestedSpan");
        assertThat(reporter.getSpans().get(0).getContext().getTags()).containsEntry("foo", "bar");
        assertThat(reporter.getSpans().get(0).isChildOf(reporter.getSpans().get(1))).isTrue();

        assertThat(reporter.getSpans().get(1).getName().toString()).isEqualTo("AnnotationTestClass#span");
        assertThat(reporter.getSpans().get(1).isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    void testCaptureTransactionAnnotationException() {
        assertThatThrownBy(AnnotationTestClass::transactionWithException).isInstanceOf(RuntimeException.class);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("AnnotationTestClass#transactionWithException");
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("catch me if you can");
    }

    public static class AnnotationTestClass {

        @CaptureTransaction
        static void transactionWithException() {
            throw new RuntimeException("catch me if you can");
        }

        @CaptureTransaction("transaction")
        void transaction() {
            ElasticApm.currentTransaction().addTag("foo", "bar");
            span();
        }

        @CaptureSpan
        void span() {
            nestedSpan();
        }

        @CaptureSpan
        void nestedSpan() {
            ElasticApm.currentSpan().addTag("foo", "bar");
        }
    }
}
