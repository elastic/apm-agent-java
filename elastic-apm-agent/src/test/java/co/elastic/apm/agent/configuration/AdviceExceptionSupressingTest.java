/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdviceExceptionSupressingTest {

    @Test
    void testAllAdvicesSuppressExceptions() {
        List<ElasticApmInstrumentation> instrumentations = DependencyInjectingServiceLoader.load(ElasticApmInstrumentation.class, MockTracer.create());
        for (ElasticApmInstrumentation instrumentation : instrumentations) {
            for (Method method : instrumentation.getClass().getDeclaredMethods()) {
                Advice.OnMethodEnter onMethodEnter = method.getAnnotation(Advice.OnMethodEnter.class);
                if (onMethodEnter != null) {
                    assertThat(onMethodEnter.suppress()).isEqualTo(Throwable.class);
                }
                Advice.OnMethodExit onMethodExit = method.getAnnotation(Advice.OnMethodExit.class);
                if (onMethodExit != null) {
                    assertThat(onMethodExit.suppress()).isEqualTo(Throwable.class);
                }
            }
        }
    }
}
