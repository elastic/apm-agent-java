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
package co.elastic.apm.agent.bci.classloading;

import java.lang.invoke.MethodHandles;

/**
 * This class is injected into every {@link IndyPluginClassLoader} in {@link co.elastic.apm.agent.bci.IndyBootstrap#bootstrap}
 * so that the bootstrap can use a {@link MethodHandles.Lookup} with a lookup class from within the {@link IndyPluginClassLoader},
 * instead of calling {@link MethodHandles#lookup()} which uses the caller class as the lookup class.
 * <p>
 * This circumvents a nasty JVM bug that's described <a href="https://github.com/elastic/apm-agent-java/issues/1450">here</a>.
 * </p>
 */
public class LookupExposer {

    public static MethodHandles.Lookup getLookup() {
        return MethodHandles.lookup();
    }
}
