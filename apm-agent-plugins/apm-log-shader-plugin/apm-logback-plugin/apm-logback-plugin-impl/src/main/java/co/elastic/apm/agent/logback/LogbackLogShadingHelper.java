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
package co.elastic.apm.agent.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import co.elastic.apm.agent.log.shader.AbstractLogShadingHelper;
import co.elastic.apm.agent.log.shader.Utils;
import co.elastic.logging.logback.EcsEncoder;

import javax.annotation.Nullable;

class LogbackLogShadingHelper extends AbstractLogShadingHelper<OutputStreamAppender<ILoggingEvent>, Encoder<ILoggingEvent>> {

    private static final LoggerContext defaultLoggerContext = new LoggerContext();

    private static final LogbackLogShadingHelper INSTANCE = new LogbackLogShadingHelper();

    static LogbackLogShadingHelper instance() {
        return INSTANCE;
    }

    private LogbackLogShadingHelper() {
    }

    @Override
    protected Encoder<ILoggingEvent> getFormatterFrom(OutputStreamAppender<ILoggingEvent> appender) {
        return appender.getEncoder();
    }

    @Override
    protected void setFormatter(OutputStreamAppender<ILoggingEvent> appender, Encoder<ILoggingEvent> encoder) {
        // Required for versions up to 1.2. No need to init the original encoder, as it is initialized by the framework
        if (encoder instanceof EcsEncoder) {
            ((EcsEncoder) encoder).init(appender.getOutputStream());
        }
        appender.setEncoder(encoder);
    }

    @Override
    protected String getAppenderName(OutputStreamAppender<ILoggingEvent> appender) {
        return appender.getName();
    }

    @Override
    protected Encoder<ILoggingEvent> createEcsFormatter(String eventDataset, @Nullable String serviceName) {
        EcsEncoder ecsEncoder = new EcsEncoder();
        ecsEncoder.setServiceName(serviceName);
        ecsEncoder.setEventDataset(eventDataset);
        ecsEncoder.setIncludeMarkers(false);
        ecsEncoder.setIncludeOrigin(false);
        ecsEncoder.setStackTraceAsArray(false);
        return ecsEncoder;
    }

    @Nullable
    @Override
    protected OutputStreamAppender<ILoggingEvent> createAndStartEcsAppender(OutputStreamAppender<ILoggingEvent> originalAppender,
                                                                            String ecsAppenderName, Encoder<ILoggingEvent> ecsEncoder) {
        RollingFileAppender<ILoggingEvent> ecsAppender = null;
        if (originalAppender instanceof FileAppender) {
            FileAppender<ILoggingEvent> fileAppender = (FileAppender<ILoggingEvent>) originalAppender;
            ecsAppender = new RollingFileAppender<>();
            ecsAppender.setEncoder(ecsEncoder);

            String shadeFile = Utils.computeShadeLogFilePath(fileAppender.getFile(), getConfiguredShadeDir());
            ecsAppender.setFile(shadeFile);

            FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
            rollingPolicy.setMinIndex(1);
            rollingPolicy.setMaxIndex(1);
            rollingPolicy.setFileNamePattern(shadeFile + ".%i");
            rollingPolicy.setParent(ecsAppender);
            rollingPolicy.setContext(defaultLoggerContext);
            rollingPolicy.start();
            ecsAppender.setRollingPolicy(rollingPolicy);

            SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
            try {
                VersionUtils.setMaxFileSize(triggeringPolicy, getMaxLogFileSize());
            } catch (Throwable throwable) {
                // We cannot log here because this plugin escapes slf4j package reallocation.
                logInfo("Failed to set max file size for log shader file-rolling strategy. Using the default " +
                    "Logback setting instead - " + SizeBasedTriggeringPolicy.DEFAULT_MAX_FILE_SIZE + ". Error message: " +
                    throwable.getMessage());
            }
            triggeringPolicy.setContext(defaultLoggerContext);
            triggeringPolicy.start();
            ecsAppender.setTriggeringPolicy(triggeringPolicy);

            ecsAppender.setContext(defaultLoggerContext);
            try {
                VersionUtils.copyImmediateFlushSetting(originalAppender);
            } catch (Throwable throwable) {
                // We cannot log here because this plugin escapes slf4j package reallocation.
                // Writing to System out may be too much for this.
            }
            ecsAppender.setAppend(true);
            ecsAppender.setName(ecsAppenderName);
            ecsAppender.start();
        }
        return ecsAppender;
    }

    @Override
    protected void closeShadeAppender(OutputStreamAppender<ILoggingEvent> shadeAppender) {
        shadeAppender.stop();
    }
}
