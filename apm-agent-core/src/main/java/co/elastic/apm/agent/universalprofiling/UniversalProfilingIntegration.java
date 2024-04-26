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
package co.elastic.apm.agent.universalprofiling;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.UniversalProfilingConfiguration;
import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.otel.JvmtiAccess;
import co.elastic.otel.UniversalProfilingCorrelation;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class UniversalProfilingIntegration {

    private static final Logger log = LoggerFactory.getLogger(UniversalProfilingIntegration.class);

    private volatile ElasticApmTracer tracer;

    // Visible for testing
    volatile boolean isActive = false;

    private ActivationListener activationListener = new ActivationListener() {

        @Override
        public void beforeActivate(AbstractSpan<?> span) {
            ProfilerSharedMemoryWriter.updateThreadCorrelationStorage(span);
        }

        @Override
        public void afterDeactivate(@Nullable AbstractSpan<?> deactivatedSpan) {
            ElasticContext<?> currentContext = tracer.currentContext();
            ProfilerSharedMemoryWriter.updateThreadCorrelationStorage(currentContext.getSpan());
        }
    };

    public void start(ElasticApmTracer tracer) {
        this.tracer = tracer;
        UniversalProfilingConfiguration config = tracer.getConfig(UniversalProfilingConfiguration.class);
        if (!config.isEnabled()) {
            return;
        }
        if (SystemInfo.isWindows(System.getProperty("os.name"))) {
            log.warn("Universal profiling integration is not supported on Windows");
            return;
        }
        try {
            log.debug("Starting universal profiling correlation");

            CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
            ByteBuffer processCorrelationStorage = ProfilerSharedMemoryWriter.generateProcessCorrelationStorage(
                coreConfig.getServiceName(), coreConfig.getEnvironment(), "");
            UniversalProfilingCorrelation.setProcessStorage(processCorrelationStorage);

            isActive = true;
            tracer.registerSpanListener(activationListener);
        } catch (Exception e) {
            log.error("Failed to start universal profiling integration", e);
        }
    }

    public void stop() {
        try {
            if (isActive) {
                JvmtiAccess.destroy();
            }
        } catch (Exception e) {
            log.error("Failed to stop universal profiling integration", e);
        }
    }

    public void afterTransactionStart(Transaction startedTransaction) {
        //TODO: store the transaction in a map for correlating with profiling data
    }

    /**
     * This method will get invoked for ended transactions which shall be reported.
     * This method is responsible for eventually reporting those transactions.
     * <p>
     * The tracer calls this method instead of reporting directly in order to allow
     * the correlation to delay the transaction reporting until all correlation
     * data has been received from the universal profiling host agent.
     *
     * @param endedTransaction the transaction to be reported
     */
    public void correlateAndReport(Transaction endedTransaction) {
        //TODO: perform correlation and report after buffering for a certain delay
        tracer.getReporter().report(endedTransaction);
    }

    public void drop(Transaction endedTransaction) {
        //TODO: remove dropped transactions from correlation storage without reporting
    }
}
