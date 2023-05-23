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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.client.handler.ClientExecutionParams;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSyncClientHandlerInstrumentationTest {

    @BeforeEach
    public void clearRedactedExceptions() {
        BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions.clear();
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions).isEmpty();

    }

    @Test
    public void checkNoRedactedExceptionIfNoExceptionThrown() {
        BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO = new JvmRuntimeInfo("17.0.1", "OpenJDK 64-Bit Server VM", "Eclipse Adoptium", "17.0.1+12");
        assertThat(BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO.isCoretto()).isFalse();
        assertThat(exerciseRedactedException(null)).isEqualTo(Outcome.SUCCESS);
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions).isEmpty();
    }

    @Test
    public void checkRedactedExceptionWhenExceptionThrownButNotCorretto() {
        BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO = new JvmRuntimeInfo("17.0.1", "OpenJDK 64-Bit Server VM", "Eclipse Adoptium", "17.0.1+12");
        assertThat(BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO.isCoretto()).isFalse();
        assertThat(exerciseRedactedException(new Exception("test1"))).isEqualTo(Outcome.FAILURE);
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions).isEmpty();
    }

    @Test
    public void checkRedactedExceptionWhenExceptionThrownOnCorretto17() {
        BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO = new JvmRuntimeInfo("17.0.5", "OpenJDK 64-Bit Server VM", "Amazon.com Inc.", "17.0.5+8-LTS");
        assertThat(BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO.isCoretto()).isTrue();
        assertThat(BaseSyncClientHandlerInstrumentation.JVM_RUNTIME_INFO.getMajorVersion()).isGreaterThan(16);
        assertThat(exerciseRedactedException(new Exception("test2"))).isEqualTo(Outcome.FAILURE);
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions).isNotEmpty();
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions.get(this.getClass().getName())).isNotNull();
    }

    public Outcome exerciseRedactedException(Exception canBeNull) {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        ElasticApmTracer tracer = mockInstrumentationSetup.getTracer();

        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        transaction
            .withName("exception-test")
            .withType("test")
            .withResult("success")
            .withOutcome(Outcome.SUCCESS)
            .activate();

        Span span = tracer.createExitChildSpan();
        span.withType("storage")
            .withSubtype("s3")
            .withAction("operationName");
        span.getContext().getDestination().withAddress("127.0.0.1").withPort(5432);
        span.activate();
        assertThat(span).isNotNull();

        ExecutionContext executionContext = ExecutionContext
            .builder()
            .executionAttributes(
                ExecutionAttributes
                    .builder()
                    .put(AwsSignerExecutionAttribute.SERVICE_NAME,"S3")
                    .build()
            )
            .build();
        ClientExecutionParams<SdkRequest,?> clientExecutionParams = new ClientExecutionParams<>();
        BaseSyncClientHandlerInstrumentation.AdviceClass.exitDoExecute(clientExecutionParams, executionContext, span, canBeNull, null, this);
        return span.getOutcome();
    }

    @Test
    public void checkRedactedExceptionStackTraceIsCorrectForNoMatchingClass() {
        checkRedactedExceptionStackTraceFor("won't match any class", BaseSyncClientHandlerInstrumentation.RedactedException.class.getName());
    }

    @Test
    public void checkRedactedExceptionStackTraceIsCorrectForClasslevel2() {
        String fakeTopOfStackClassname = ClassLevel2.class.getName();
        checkRedactedExceptionStackTraceFor(fakeTopOfStackClassname, fakeTopOfStackClassname);
    }

    public void checkRedactedExceptionStackTraceFor(String classname, String matches) {
        ClassLevel1.level1(classname);
        BaseSyncClientHandlerInstrumentation.RedactedException exception = BaseSyncClientHandlerInstrumentation.RedactedException.getInstance(classname);
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions).isNotEmpty();
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions.get(classname)).isNotNull();
        assertThat(BaseSyncClientHandlerInstrumentation.RedactedException.Exceptions.get(classname)).isEqualTo(exception);
        assertThat(exception.getStackTrace()[0].getClassName()).isEqualTo(matches);
    }

    private static void level4(String fakeTopOfStackClassname) {
        BaseSyncClientHandlerInstrumentation.RedactedException.getInstance(fakeTopOfStackClassname);
    }

    static class ClassLevel1 {
        static void level1(String fakeTopOfStackClassname) {
            ClassLevel2.level2(fakeTopOfStackClassname);
        }
    }

    static class ClassLevel2 {
        static void level2(String fakeTopOfStackClassname) {
            ClassLevel3.level3(fakeTopOfStackClassname);
        }
    }

    static class ClassLevel3 {
        static void level3(String fakeTopOfStackClassname) {
            BaseSyncClientHandlerInstrumentationTest.level4(fakeTopOfStackClassname);
        }
    }

}
