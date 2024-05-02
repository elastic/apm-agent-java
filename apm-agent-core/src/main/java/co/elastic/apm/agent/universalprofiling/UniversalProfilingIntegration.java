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
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.otel.JvmtiAccess;
import co.elastic.otel.UniversalProfilingCorrelation;
import co.elastic.otel.profiler.DecodeException;
import co.elastic.otel.profiler.ProfilerMessage;
import co.elastic.otel.profiler.ProfilerRegistrationMessage;
import co.elastic.otel.profiler.TraceCorrelationMessage;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class UniversalProfilingIntegration {

    /**
     * The frequency at which the processor polls the unix domain socket for new messages from the
     * profiler.
     */
    static final long POLL_FREQUENCY_MS = 20;

    private static final Logger log = LoggerFactory.getLogger(UniversalProfilingIntegration.class);

    private volatile ElasticApmTracer tracer;

    // Visible for testing
    volatile boolean isActive = false;

    // Visible for testing
    String socketPath = null;

    private ScheduledExecutorService executor;

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

            socketPath = openProfilerSocket(config.getSocketDir());

            CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
            ByteBuffer processCorrelationStorage = ProfilerSharedMemoryWriter.generateProcessCorrelationStorage(
                coreConfig.getServiceName(), coreConfig.getEnvironment(), socketPath);
            UniversalProfilingCorrelation.setProcessStorage(processCorrelationStorage);

            executor = ExecutorUtils.createSingleThreadSchedulingDaemonPool("profiling-integration");
            executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    periodicTimer();
                }
            }, POLL_FREQUENCY_MS, POLL_FREQUENCY_MS, TimeUnit.MILLISECONDS);

            isActive = true;
            tracer.registerSpanListener(activationListener);
        } catch (Exception e) {
            log.error("Failed to start universal profiling integration", e);
            if (socketPath != null) {
                try {
                    UniversalProfilingCorrelation.stopProfilerReturnChannel();
                    socketPath = null;
                } catch (Exception e2) {
                    log.error("Failed to clean up universal profiling integration socket", e2);
                }
            }
        }
    }

    private void periodicTimer() {
        consumeProfilerMessages();
    }

    public void stop() {
        try {
            if (executor != null) {
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
                executor = null;
            }
            if (isActive) {
                UniversalProfilingCorrelation.stopProfilerReturnChannel();
                JvmtiAccess.destroy();
                isActive = false;
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


    private String openProfilerSocket(String socketDir) {
        Path dir = Paths.get(socketDir);
        if (!Files.exists(dir) && !dir.toFile().mkdirs()) {
            throw new IllegalArgumentException("Could not create directory '" + socketDir + "'");
        }
        Path socketFile;
        do {
            socketFile = dir.resolve(randomSocketFileName());
        } while (Files.exists(socketFile));

        String absolutePath = socketFile.toAbsolutePath().toString();
        log.debug("Opening profiler correlation socket {}", absolutePath);
        UniversalProfilingCorrelation.startProfilerReturnChannel(absolutePath);
        return absolutePath;
    }

    private String randomSocketFileName() {
        StringBuilder name = new StringBuilder("essock");
        String allowedChars = "abcdefghijklmonpqrstuvwxzyABCDEFGHIJKLMONPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        for (int i = 0; i < 8; i++) {
            int idx = rnd.nextInt(allowedChars.length());
            name.append(allowedChars.charAt(idx));
        }
        return name.toString();
    }

    private void consumeProfilerMessages() {
        try {
            while (true) {
                try {
                    ProfilerMessage message =
                        UniversalProfilingCorrelation.readProfilerReturnChannelMessage();
                    if (message == null) {
                        break;
                    } else if (message instanceof TraceCorrelationMessage) {
                        handleMessage((TraceCorrelationMessage) message);
                    } else if (message instanceof ProfilerRegistrationMessage) {
                        handleMessage((ProfilerRegistrationMessage) message);
                    } else {
                        log.debug("Received unknown message type from profiler: {}", message);
                    }
                } catch (DecodeException e) {
                    log.warn("Failed to read profiler message", e);
                    // intentionally no break here, subsequent messages might be decodeable
                }
            }
        } catch (Exception e) {
            log.error("Cannot read from profiler socket", e);
        }
    }

    private void handleMessage(ProfilerRegistrationMessage message) {
        //TODO: handle message
    }

    private void handleMessage(TraceCorrelationMessage message) {
        //TODO: handle message
    }
}
