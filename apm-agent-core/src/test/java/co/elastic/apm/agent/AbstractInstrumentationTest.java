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
package co.elastic.apm.agent;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractInstrumentationTest {
    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;
    protected static ConfigurationRegistry config;
    protected static TestObjectPoolFactory objectPoolFactory;
    private boolean validateRecycling = true;

    @BeforeAll
    @BeforeClass
    public static synchronized void beforeAll() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        config = mockInstrumentationSetup.getConfig();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
        reporter = mockInstrumentationSetup.getReporter();

        assertThat(tracer.isRunning()).isTrue();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterAll
    @AfterClass
    public static synchronized void afterAll() {
        ElasticApmAgent.reset();
    }

    protected void disableRecyclingValidation() {
        validateRecycling = false;
    }

    public static Tracer getTracer() {
        return tracer;
    }

    public static MockReporter getReporter() {
        return reporter;
    }

    public static ConfigurationRegistry getConfig() {
        return config;
    }

    @After
    @AfterEach
    public final void cleanUp() {
        // re-enable all reporter checks
        reporter.resetChecks();

        SpyConfiguration.reset(config);
        try {
            if (validateRecycling) {
                reporter.assertRecycledAfterDecrementingReferences();
                objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
            }
        } finally {
            // one faulty test should not affect others
            reporter.resetWithoutRecycling();
            objectPoolFactory.reset();
            // resume tracer in case it has been paused
            // otherwise the 1st test that pauses tracer will have side effects on others
            if (!tracer.isRunning()) {
                TracerInternalApiUtils.resumeTracer(tracer);
            }
        }
        tracer.resetServiceNameOverrides();

        assertThat(tracer.getActive())
            .describedAs("nothing should be left active at end of test, failure will likely indicate a span/transaction still active")
            .isNull();
    }

    /**
     * Creates a test root transaction with default values applied.
     *
     * <p>This method should be used to create a transaction used to test execution of a given instrumentation
     * when an active transaction is available, for example to create child spans.</p>
     *
     * @param name transaction name
     * @return root transaction
     */
    protected Transaction startTestRootTransaction(String name) {
        Transaction transaction = tracer.startRootTransaction(null);

        assertThat(transaction).isNotNull();

        return transaction
            .withName(name)
            .withType("test")
            .withResult("success")
            .withOutcome(Outcome.SUCCESS)
            .activate();
    }

    /**
     * Creates a test root transaction
     *
     * @return root transaction
     */
    protected Transaction startTestRootTransaction() {
        return startTestRootTransaction("test root transaction");
    }

    /**
     * Triggers a GC + stale entry cleanup in order to trigger GC-based expiration
     *
     * @param map   map to flush
     * @param count number of cleanup loops to execute
     */
    protected static void flushGcExpiry(WeakConcurrentMap<?, ?> map, int count) {
        // note: we can't rely on map size as it might report zero when not empty
        int left = count;
        do {
            System.out.printf("flushGcExpiry - before gc execution%n");
            long start = System.currentTimeMillis();
            System.gc();
            long duration = System.currentTimeMillis() - start;
            System.out.printf("flushGcExpiry - after gc execution %d ms%n", duration);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // silently ignored
            }
            map.expungeStaleEntries();
        } while (left-- > 0);
    }
}
