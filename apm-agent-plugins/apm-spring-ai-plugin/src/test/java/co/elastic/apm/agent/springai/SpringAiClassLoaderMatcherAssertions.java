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
package co.elastic.apm.agent.springai;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAiClassLoaderMatcherAssertions {

    private static final String MODEL_CLASS = "org/springframework/ai/model/Model.class";
    private static final String AI_OPERATION_TYPE_CLASS =
        "org/springframework/ai/observation/conventions/AiOperationType.class";
    private static final String VECTOR_STORE_CLASS =
        "org/springframework/ai/vectorstore/observation/AbstractObservationVectorStore.class";
    private static final String SPRING_AI_2_MARKER_CLASS =
        "org/springframework/ai/embedding/DefaultEmbeddingOptions$Builder.class";

    public static void assertClassLoaderMatcher(ClassLoader classLoader, boolean supported) {
        assertCommonSpringAiClassesPresent(classLoader);
        if (supported) {
            assertThat(classLoader.getResource(SPRING_AI_2_MARKER_CLASS)).isNotNull();
        } else {
            assertThat(classLoader.getResource(SPRING_AI_2_MARKER_CLASS)).isNull();
        }
        assertThat(instrumentations()).allSatisfy(instrumentation ->
            assertThat(instrumentation.getClassLoaderMatcher().matches(classLoader)).isEqualTo(supported));
    }

    private static void assertCommonSpringAiClassesPresent(ClassLoader classLoader) {
        assertThat(classLoader.getResource(MODEL_CLASS)).isNotNull();
        assertThat(classLoader.getResource(AI_OPERATION_TYPE_CLASS)).isNotNull();
        assertThat(classLoader.getResource(VECTOR_STORE_CLASS)).isNotNull();
    }

    private static List<AbstractSpringAiInstrumentation> instrumentations() {
        return Arrays.asList(
            new ChatModelInstrumentation(),
            new StreamingChatModelInstrumentation(),
            new EmbeddingModelInstrumentation(),
            new VectorStoreInstrumentation(),
            new VectorStoreQueryInstrumentation(),
            new ToolCallbackInstrumentation()
        );
    }
}
