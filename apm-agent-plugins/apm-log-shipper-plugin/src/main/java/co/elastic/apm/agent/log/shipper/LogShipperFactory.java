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
package co.elastic.apm.agent.log.shipper;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.logging.Log4j2ConfigurationFactory;
import co.elastic.apm.agent.logging.LogFormat;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.common.ThreadUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class LogShipperFactory extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(LogShipperFactory.class);

    private static final int SHUTDOWN_TIMEOUT = 5000;
    private static final int BUFFER_SIZE = 1024 * 4;
    private static final int MAX_LINES_PER_CYCLE = 100;
    private static final int IDLE_TIME_MS = 250;

    private final FileTailer fileTailer;

    public LogShipperFactory(ElasticApmTracer tracer) {
        ApmServerClient apmServerClient = tracer.getApmServerClient();
        DslJsonSerializer serializer = new DslJsonSerializer(tracer.getConfig(StacktraceConfiguration.class), apmServerClient, tracer.getMetaDataFuture());
        ApmServerLogShipper logShipper = new ApmServerLogShipper(apmServerClient, tracer.getConfig(ReporterConfiguration.class), serializer);
        ExecutorUtils.SingleNamedThreadFactory threadFactory = new ExecutorUtils.SingleNamedThreadFactory(ThreadUtils.addElasticApmThreadPrefix("log-shipper"));
        fileTailer = new FileTailer(logShipper, BUFFER_SIZE, MAX_LINES_PER_CYCLE, IDLE_TIME_MS, threadFactory);
    }

    @Override
    public void start(ElasticApmTracer tracer) throws IOException {
        LoggingConfiguration config = tracer.getConfig(LoggingConfiguration.class);
        String logFile = config.getLogFile();
        if (!config.isShipAgentLogs()) {
            return;
        }
        if (!tracer.getApmServerClient().supportsLogsEndpoint()) {
            logger.warn("This version of APM Server does not support the logs endpoint. Consider updating to version 7.9+.");
            return;
        }
        if (logFile.equals(LoggingConfiguration.SYSTEM_OUT)) {
            TailableFile tailableFile = fileTailer.tailFile(Log4j2ConfigurationFactory.getTempLogFile(tracer.getEphemeralId()));
            logger.debug("Tailing temp agent log file {}", tailableFile);
            tailableFile.deleteStateFileOnExit();
            fileTailer.start();
        } else if (config.getLogFormatFile() == LogFormat.JSON) {
            fileTailer.tailFile(new File(logFile));
            logger.debug("Tailing agent log file {}", logFile);
            fileTailer.start();
        } else {
            logger.warn("Can't ship agent log file if format is plain text. Set {}={}", LoggingConfiguration.LOG_FORMAT_FILE_KEY, LogFormat.JSON);
        }
    }

    @Override
    public void stop() throws Exception {
        fileTailer.stop(SHUTDOWN_TIMEOUT);
    }
}
