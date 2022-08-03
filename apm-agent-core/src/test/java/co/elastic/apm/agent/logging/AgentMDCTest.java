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
package co.elastic.apm.agent.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMDCTest {

    @BeforeEach
    void before(){
        // prevently empty if any other test have left something
        AgentMDC.getEntries().clear();
    }

    @AfterEach
    void after() {
        AgentMDC.getEntries().clear();
        assertThat(AgentMDC.getEntries()).isEmpty();
    }

    @Test
    void ignoreNullParams() {
        AgentMDC.put(null, null);
        AgentMDC.put("", null);

        assertThat(AgentMDC.getEntries()).isEmpty();

        AgentMDC.remove(null);
    }

    @Test
    void emptyMdc() {
        Map<String, String> entries = AgentMDC.getEntries();
        assertThat(entries).isEmpty();

        assertThat(AgentMDC.getEntries()).isSameAs(entries);

        // should be a no-op
        AgentMDC.remove("missing");
    }

    @Test
    void putRemoveSingleEntry() {
        AgentMDC.put("hello", "world");
        assertThat(AgentMDC.getEntries()).containsEntry("hello", "world");

        AgentMDC.remove("hello");
        assertThat(AgentMDC.getEntries()).isEmpty();
    }
}
