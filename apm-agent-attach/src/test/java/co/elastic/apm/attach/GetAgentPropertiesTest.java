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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetAgentPropertiesTest {

    @Test
    void testGetProperties() throws Exception {
        System.setProperty("foo", "bar");
        try {
            assertThat(GetAgentProperties.getAgentAndSystemPropertiesCurrentUser(JvmInfo.CURRENT_PID))
                .containsEntry("foo", "bar");
            assertThat(GetAgentProperties.getAgentAndSystemPropertiesSwitchUser(JvmInfo.CURRENT_PID, UserRegistry.empty().getCurrentUser()))
                .containsEntry("foo", "bar");
        } finally {
            System.clearProperty("foo");
        }
    }

    @Test
    void testGetPropertiesEqual() throws Exception {
        assertThat(GetAgentProperties.getAgentAndSystemPropertiesSwitchUser(JvmInfo.CURRENT_PID, UserRegistry.empty().getCurrentUser()))
            .isEqualTo(GetAgentProperties.getAgentAndSystemPropertiesCurrentUser(JvmInfo.CURRENT_PID));
    }
}
