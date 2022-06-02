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
package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;

public class CdiJakartaeeServletContainerTestApp extends TestApp {

    public CdiJakartaeeServletContainerTestApp() {
        super("../cdi-jakartaee-app/cdi-jakartaee-app-standalone",
            "cdi-jakartaee-app.war",
            "cdi-jakartaee-app",
            "status.html",
            "CDI Jakarta App",
            null);
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest containerIntegrationTest) throws Exception {
        new CdiJakartaeeApplicationServerTestApp().test(containerIntegrationTest);
    }
}
