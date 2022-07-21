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
package specs;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;

import java.util.Collections;
import java.util.Map;

public class BaseStepDefinitions {

    private final ScenarioState scenarioState;

    public BaseStepDefinitions(ScenarioState scenarioState) {
        this.scenarioState = scenarioState;
    }

    @Given("an agent")
    public void initAgent() {
        scenarioState.initTracer(Collections.emptyMap());
    }

    @Given("an agent configured with")
    public void initAndConfigureAgent(Map<String, String> configOptions) {
        scenarioState.initTracer(configOptions);
    }

    @Given("an active transaction")
    public void startTransaction() {
        scenarioState.startTransaction();
    }

    @Given("an active span")
    public void startSpan() {
        // spans can't exist outside of a transaction, thus we have to create it if not explicitly asked to
        scenarioState.startRootTransactionIfRequired();
        scenarioState.startSpan();
    }

    @Given("the {} ends")
    public void endContext(String context) {
        scenarioState.getContext(context).end();
    }

    @ParameterType("transaction|span")
    public String contextType(String contextType) {
        return contextType;
    }
}
