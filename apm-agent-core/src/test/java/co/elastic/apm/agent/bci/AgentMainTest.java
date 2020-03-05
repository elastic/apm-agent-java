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
package co.elastic.apm.agent.bci;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMainTest {

    @Test
    void java6AndEarlierNotSupported() {
        checkNotSupported("1.5.0");
        checkNotSupported("1.5.0-hello");
        checkNotSupported("1.5.0_1");
        checkNotSupported("1.5.0_1-hello");
        checkNotSupported("1.6.0");
        checkNotSupported("1.6.0-hello");
        checkNotSupported("1.6.0_42");
        checkNotSupported("1.6.0_42-hello");
    }

    @Test
    void java7AllVersionsSupported() {
        checkSupported("1.7.0");
        checkSupported("1.7.0-hello");
        checkSupported("1.7.0_1");
        checkSupported("1.7.0_1-hello");
        checkSupported("1.7.0_241");
        checkSupported("1.7.0_241-hello");
    }

    @Test
    void java8OnlySupportedAfterUpdate40() {
        checkNotSupported("1.8.0");
        checkNotSupported("1.8.0-hello");
        checkNotSupported("1.8.0_1");
        checkNotSupported("1.8.0_1-hello");
        checkNotSupported("1.8.0_39");
        checkNotSupported("1.8.0_39-hello");
        checkSupported("1.8.0_40");
        checkSupported("1.8.0_40-hello");
        checkSupported("1.8.0_241");
        checkSupported("1.8.0_241-hello");
    }

    @Test
    void java9AndLaterAllVersionsSupported() {
        checkSupported("9");
        checkSupported("9.0.1");
        checkSupported("9.0.4");
        checkSupported("10");
        checkSupported("10.0.1");
        checkSupported("10.0.2");
        checkSupported("11");
        checkSupported("11.0.1");
        checkSupported("11.0.2");
        checkSupported("11.0.3");
        checkSupported("11.0.4");
        checkSupported("11.0.5");
        checkSupported("11.0.6");
        checkSupported("12");
        checkSupported("12.0.1");
        checkSupported("12.0.2");
        checkSupported("13");
        checkSupported("13.0.1");
        checkSupported("13.0.2");
        checkSupported("14");
    }

    @Test
    void notSupportedwithGarbage() {
        checkNotSupported("1.8.0_aaa");
    }

    private static void checkSupported(String version) {
        assertThat(AgentMain.isJavaVersionSupported(version))
            .describedAs("java.version = %s should be supported", version)
            .isTrue();
    }

    private static void checkNotSupported(String version) {
        assertThat(AgentMain.isJavaVersionSupported(version))
            .describedAs("java.version = %s should not be supported", version)
            .isFalse();
    }

}
