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
package co.elastic.apm.external.plugin.servlet;

import co.elastic.apm.plugin.test.TestClass;

import java.io.IOException;

/**
 * This Servlet demonstrates a full integration test through a webapp that is tested on all Servlet containers in
 * `integration-tests/application-server-integration-tests` (see {@code ExternalPluginTestApp}). When tested this way,
 * the plugin is loaded as a jar from the configured plugin directory, including the instrumentation class itself and
 * the META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation file.
 *
 * Since the path corresponding this Servlet is ignored, a transaction is created through the Servlet instrumentation.
 * Therefore, the instrumented method ({@link TestClass#traceMe(boolean)}) invocation targeted by the external plugin
 * should be captured as a transaction.
 */
public class ExternalPluginTestTransactionServlet extends javax.servlet.http.HttpServlet {

    protected void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        try {
            // Using an external library type that is expected to be instrumented by the external plugin.
            // A transaction should not be created by the general Servlet API instrumentation because we ignore the
            // corresponding URL, so we expect to get a transaction for the TestClass#traceMe() invocation.
            // We also expect an error related to this transaction.
            // See ExternalPluginTestApp in `integration-tests/application-server-integration-tests`.
            new TestClass().traceMe(true);
        } catch (IllegalStateException e) {
            // do nothing - expected
        }
        response.getWriter().append("Success");
    }
}
