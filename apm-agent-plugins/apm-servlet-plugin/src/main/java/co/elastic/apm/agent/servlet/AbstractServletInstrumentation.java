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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.util.PackageScanner;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.servlet.ServletInstrumentation.SERVLET_API;

public abstract class AbstractServletInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    @Override
    public List<String> helpers() throws Exception {
        return PackageScanner.getClassNames(getClass().getPackageName());
    }

    @Override
    public final ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // this class has been introduced in servlet spec 3.0
        // choice of class name to use for this test does not work as expected across all application servers
        // for example, 'javax.servlet.annotation.WebServlet' annotation is not working as expected on Payara
        return CustomElementMatchers.classLoaderCanLoadClass("javax.servlet.AsyncContext");
    }
}
