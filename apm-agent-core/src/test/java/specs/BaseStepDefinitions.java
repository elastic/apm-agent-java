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

import io.cucumber.java.en.Given;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseStepDefinitions {

    private final SpecTracerState state;

    @Given("an agent")
    public void initAgent() {
        // not used, use before/after hooks instead for init & cleanup
    }

    public BaseStepDefinitions(SpecTracerState state) {
        this.state = state;
    }

    @Given("an active transaction")
    public void startTransaction() {
        assertThat(state.getTransaction()).isNull();

        state.startTransaction();
    }

    @Given("an active span")
    public void startSpan() {
        // spans can't exist outside  a transaction, thus we have to create it if not explicitly asked to
        state.startRootTransactionIfRequired();

        state.startSpan();
    }
}
