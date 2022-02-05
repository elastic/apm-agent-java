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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentationUsageUtilTest {

    private static class NoopInstrumentation extends ElasticApmInstrumentation {
        private final Collection instrumentationGroups;

        public NoopInstrumentation(Collection<String> instrumentationGroups) {
            this.instrumentationGroups = instrumentationGroups;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return null;
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return null;
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return instrumentationGroups;
        }
    }

    @AfterEach
    void clearFoo() {
        InstrumentationUsageUtil.reset();
    }

    @Test
    void test1() {
        InstrumentationUsageUtil.addInstrumentation(new NoopInstrumentation(Set.of("a", "b")));

        assertThat(InstrumentationUsageUtil.getUsedInstrumentationGroups()).isEmpty();
    }

    @Test
    void test2() {
        NoopInstrumentation instrumentation = new NoopInstrumentation(Set.of("a", "b"));

        InstrumentationUsageUtil.addInstrumentation(instrumentation);
        InstrumentationUsageUtil.addUsedInstrumentation(instrumentation);

        assertThat(InstrumentationUsageUtil.getUsedInstrumentationGroups()).hasSameElementsAs(List.of("a", "b"));
    }

    @Test
    void test3() {
        NoopInstrumentation instrumentation1 = new NoopInstrumentation(Set.of("a", "b"));
        NoopInstrumentation instrumentation2 = new NoopInstrumentation(Set.of("a", "c"));

        InstrumentationUsageUtil.addInstrumentation(instrumentation1);
        InstrumentationUsageUtil.addInstrumentation(instrumentation2);
        InstrumentationUsageUtil.addUsedInstrumentation(instrumentation1);

        assertThat(InstrumentationUsageUtil.getUsedInstrumentationGroups()).hasSameElementsAs(List.of("b"));
    }

    @Test
    void test4() {
        NoopInstrumentation instrumentation1 = new NoopInstrumentation(Set.of("a", "b"));
        NoopInstrumentation instrumentation2 = new NoopInstrumentation(Set.of("c", "d"));
        NoopInstrumentation instrumentation3 = new NoopInstrumentation(Set.of("a", "b"));

        InstrumentationUsageUtil.addInstrumentation(instrumentation1);
        InstrumentationUsageUtil.addInstrumentation(instrumentation2);
        InstrumentationUsageUtil.addInstrumentation(instrumentation3);
        InstrumentationUsageUtil.addUsedInstrumentation(instrumentation1);

        assertThat(InstrumentationUsageUtil.getUsedInstrumentationGroups()).hasSameElementsAs(List.of("a", "b"));
    }
}
