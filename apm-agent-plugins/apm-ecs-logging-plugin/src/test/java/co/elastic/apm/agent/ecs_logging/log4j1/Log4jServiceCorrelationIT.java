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
package co.elastic.apm.agent.ecs_logging.log4j1;

import co.elastic.apm.agent.ecs_logging.EcsServiceCorrelationIT;

public class Log4jServiceCorrelationIT extends EcsServiceCorrelationIT {

    @Override
    protected String getArtifactName() {
        return "log4j-ecs-layout";
    }

    @Override
    protected String getServiceNameTestClass() {
        return "co.elastic.apm.agent.ecs_logging.log4j1.Log4jServiceNameInstrumentationTest";
    }

    @Override
    protected String getServiceVersionTestClass() {
        return "co.elastic.apm.agent.ecs_logging.log4j1.Log4jServiceVersionInstrumentationTest";
    }

}
