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
package co.elastic.apm.agent.ecs_logging;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.logging.jul.JulMdc;
import co.elastic.apm.agent.logging.JulMdcAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class JulMdcInstrumentationTest extends AbstractInstrumentationTest {

    @AfterEach
    public void cleanup() {
        JulMdcAccessor.getEntries(JulMdc.class).clear();
    }

    @Test
    void accessorAccessLocalMdcClass() {
        // mostly testing a getEntries test-only method but required for other test validity

        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).isEmpty();

        JulMdc.put("key", "value");
        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).containsEntry("key", "value").hasSize(1);

        JulMdc.remove("key");
        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).isEmpty();
    }

    @Test
    void accessorPutRemoveAll() {
        // JulMdc class should be registered through instrumentation
        Logger logger = Logger.getLogger("test");
        logger.log(Level.INFO, "log something to trigger JulMdc registration");

        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).isEmpty();

        JulMdcAccessor.putAll("key", "value");
        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).containsEntry("key", "value").hasSize(1);

        JulMdcAccessor.removeAll("key");
        assertThat(JulMdcAccessor.getEntries(JulMdc.class)).isEmpty();

    }
}
