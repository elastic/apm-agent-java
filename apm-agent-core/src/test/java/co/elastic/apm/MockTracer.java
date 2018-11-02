/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.report.Reporter;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockTracer {

    /**
     * Creates a real tracer with a noop reporter and a mock configuration which returns default values which can be customized by mocking
     * the configuration.
     */
    public static ElasticApmTracer createRealTracer() {
        return createRealTracer(mock(Reporter.class));
    }

    /**
     * Creates a real tracer with a given reporter and a mock configuration which returns default values which can be customized by mocking
     * the configuration.
     */
    public static ElasticApmTracer createRealTracer(Reporter reporter) {
        return new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
    }

    /**
     * Like {@link #create(ConfigurationRegistry)} but with a {@link SpyConfiguration#createSpyConfig() mocked ConfigurationRegistry}.
     *
     * @return a mock tracer with a mocked ConfigurationRegistry
     */
    public static ElasticApmTracer create() {
        return create(SpyConfiguration.createSpyConfig());
    }

    /**
     * Creates a {@link org.mockito.Mockito#mock(Class) mocked} {@link ConfigurationRegistry}
     * for a given {@link ConfigurationRegistry}
     *
     * @return a mock tracer with the given configurationRegistry
     */
    public static ElasticApmTracer create(ConfigurationRegistry configurationRegistry) {
        final ElasticApmTracer tracer = mock(ElasticApmTracer.class);
        when(tracer.getConfigurationRegistry()).thenReturn(configurationRegistry);
        when(tracer.getConfig(any())).thenAnswer(invocation -> configurationRegistry.getConfig(invocation.getArgument(0)));
        return tracer;
    }
}
