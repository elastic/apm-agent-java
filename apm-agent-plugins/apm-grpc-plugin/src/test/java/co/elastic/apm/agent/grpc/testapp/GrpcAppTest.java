/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.grpc.testapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// this class just tests the sample application normal behavior, not the behavior when it's instrumented
class GrpcAppTest {

    // TODO : try to cancel call from client-side (only possible with async client)

    private GrpcApp app;

    @BeforeEach
    void beforeEach() throws Exception {
        app = new GrpcApp();
        app.start();
    }

    @AfterEach
    void afterEach() throws Exception {
        app.stop();
    }

    @Test
    void simpleCall() {
        checkMsg("joe", 0, "hello(joe)");
    }

    @Test
    void simpleErrorCall() {
        checkMsg(null, 0, null);
    }

    @Test
    void nestedChecks() throws Exception {
        checkMsg("joe", 0, "hello(joe)");
        checkMsg("bob", 1, "nested(1)->hello(bob)");
        checkMsg("rob", 2, "nested(2)->nested(1)->hello(rob)");
    }

    @Test
    void recommendedServerErrorHandling() {
        exceptionOrErrorCheck(null);
    }

    @Test
    void uncaughtExceptionServerErrorHandling() {
        // should be strictly identical to "recommended way to handle errors" from client perspective
        // but might differ server side
        exceptionOrErrorCheck("boom");
    }

    void exceptionOrErrorCheck(String name) {
        checkMsg(name, 0, null);
        checkMsg(name, 1, "nested(1)->error(0)");
        checkMsg(name, 2, "nested(2)->nested(1)->error(0)");
    }

    private void checkMsg(String name, int depth, String expectedMsg) {
        String msg = app.sendMessage(name, depth);
        assertThat(msg).isEqualTo(expectedMsg);
    }

}
