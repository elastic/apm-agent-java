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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.logging.log4j2.EcsLayout;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Ignore
public class Log4j2ServiceVersionInstrumentationTest extends AbstractInstrumentationTest {

    @BeforeClass
    public static void setUp() {
        when(tracer.getConfig(CoreConfiguration.class).getServiceVersion()).thenReturn("1.0");
    }

    @Test
    public void testBuildWithNoServiceVersionSet() throws JsonProcessingException {
        EcsLayout ecsLayout = EcsLayout.newBuilder().build();
        assertThat(getServiceVersion(ecsLayout.toSerializable(createLogEvent()))).isEqualTo("1.0");
    }

    @Test
    public void testBuildWithServiceVersionSet() throws JsonProcessingException {
        EcsLayout ecsLayout = EcsLayout.newBuilder().setServiceVersion("2.0").build();
        assertThat(getServiceVersion(ecsLayout.toSerializable(createLogEvent()))).isEqualTo("2.0");
    }

    private static Log4jLogEvent createLogEvent() {
        return new Log4jLogEvent("", null, "", null, new SimpleMessage(), null, null);
    }

    private static String getServiceVersion(String json) throws JsonProcessingException {
        return (String) new ObjectMapper().readValue(json, Map.class).get("service.version");
    }
}
