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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public abstract class EcsServiceVersionTest extends EcsLoggingTest {

    @BeforeEach
    public void setUp() {
        doReturn("1.0").when(tracer.getConfig(CoreConfiguration.class)).getServiceVersion();
    }

    @Test
    public void testBuildWithNoServiceVersionSet() {
        initFormatterWithoutServiceVersionSet();
        assertThat(getJson(createLogMsg(), "service.version")).isEqualTo("1.0");
    }

    protected abstract void initFormatterWithoutServiceVersionSet();

    @Test
    public void testBuildWithServiceVersionSet() {
        // this should also issue a warning as the value configured in ecs-logging differs from the agent
        initFormatterWithServiceVersion("2.0");
        assertThat(getJson(createLogMsg(), "service.version")).isEqualTo("2.0");
    }

    protected abstract void initFormatterWithServiceVersion(String version);
}
