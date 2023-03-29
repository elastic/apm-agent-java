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
package co.elastic.apm.agent.ecs_logging.log4j2;

import co.elastic.apm.agent.ecs_logging.EcsServiceNameTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

// must be tested in isolation, either used in agent or has side effects
@TestClassWithDependencyRunner.DisableOutsideOfRunner
public class Log4j2ServiceNameInstrumentationTest extends EcsServiceNameTest {

    private EcsLayout ecsLayout;

    @Override
    protected void initFormatterWithoutServiceNameSet() {
        ecsLayout = EcsLayout.newBuilder().build();
    }

    @Override
    protected void initFormatterWithServiceName(String name) {
        ecsLayout = EcsLayout.newBuilder().setServiceName(name).build();
    }

    @Override
    protected String createLogMsg() {
        Log4jLogEvent event = new Log4jLogEvent("", null, "", null, new SimpleMessage(), null, null);
        return ecsLayout.toSerializable(event);
    }

}
