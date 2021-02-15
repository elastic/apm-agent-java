/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.attach;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JvmInfoTest {

    private JvmInfo currentVm;

    @BeforeEach
    void setUp() throws Exception {
        Properties properties = GetAgentProperties.getAgentAndSystemProperties(JvmInfo.CURRENT_PID, UserRegistry.User.current());
        currentVm = JvmInfo.of(JvmInfo.CURRENT_PID, UserRegistry.getCurrentUserName(), properties);
    }

    @Test
    void testGetAgentProperties() {
        assertThat(currentVm.isVersionSupported()).isTrue();
        assertThat(System.getProperty("sun.java.command")).contains(currentVm.getMainClass());
    }

    @Test
    void testJvmSupported() {
        assertThat(isSupported("1.6.0")).isFalse();
        assertThat(isSupported("1.7.0")).isTrue();
        assertThat(isSupported("1.8.0")).isTrue();
        assertThat(isSupported("9")).isTrue();
        assertThat(isSupported("9.0.1")).isTrue();
    }

    private boolean isSupported(String s) {
        Properties properties = new Properties();
        properties.setProperty("java.version", s);
        return JvmInfo.withCurrentUser("42", properties).isVersionSupported();
    }
}
