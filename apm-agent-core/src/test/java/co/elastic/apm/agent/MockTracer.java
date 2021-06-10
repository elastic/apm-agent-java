/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.Reporter;
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
        return createRealTracer(reporter, SpyConfiguration.createSpyConfig());
    }

    /**
     * Creates a real tracer with a given reporter and a mock configuration which returns default values which can be customized by mocking
     * the configuration.
     */
    public static ElasticApmTracer createRealTracer(Reporter reporter, ConfigurationRegistry config) {

        // use an object pool that does bookkeeping to allow for extra usage checks
        TestObjectPoolFactory objectPoolFactory = new TestObjectPoolFactory();

        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            // use testing bookkeeper implementation here so we will check that no forgotten recyclable object
            // is left behind
            .withObjectPoolFactory(objectPoolFactory)
            .withLifecycleListener(ClosableLifecycleListenerAdapter.of(() -> {

                if (reporter instanceof MockReporter) {
                    ((MockReporter) reporter).assertRecycledAfterDecrementingReferences();
                }

                // checking proper object pool usage using tracer lifecycle events
                objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
            }))
            .build();

        tracer.start(false);
        return tracer;
    }

    /**
     * Creates a real tracer with a {@link MockReporter} and a mock configuration which returns default
     * values that can be customized by mocking the configuration.
     */
    public static synchronized MockInstrumentationSetup createMockInstrumentationSetup() {
        // use an object pool that does bookkeeping to allow for extra usage checks
        TestObjectPoolFactory objectPoolFactory = new TestObjectPoolFactory();

        MockReporter reporter = new MockReporter();

        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            // use testing bookkeeper implementation here so we will check that no forgotten recyclable object
            // is left behind
            .withObjectPoolFactory(objectPoolFactory)
            .withLifecycleListener(ClosableLifecycleListenerAdapter.of(() -> {
                reporter.assertRecycledAfterDecrementingReferences();
                // checking proper object pool usage using tracer lifecycle events
                objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
            }))
            .buildAndStart();
        return new MockInstrumentationSetup(
            tracer,
            reporter,
            tracer.getConfigurationRegistry(),
            objectPoolFactory
        );
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

    public static class MockInstrumentationSetup {
        private final ElasticApmTracer tracer;
        private final MockReporter reporter;
        private final ConfigurationRegistry config;
        private final TestObjectPoolFactory objectPoolFactory;

        public MockInstrumentationSetup(ElasticApmTracer tracer, MockReporter reporter, ConfigurationRegistry config, TestObjectPoolFactory objectPoolFactory) {
            this.tracer = tracer;
            this.reporter = reporter;
            this.config = config;
            this.objectPoolFactory = objectPoolFactory;
        }

        public ElasticApmTracer getTracer() {
            return tracer;
        }

        public MockReporter getReporter() {
            return reporter;
        }

        public ConfigurationRegistry getConfig() {
            return config;
        }

        public TestObjectPoolFactory getObjectPoolFactory() {
            return objectPoolFactory;
        }
    }
}
