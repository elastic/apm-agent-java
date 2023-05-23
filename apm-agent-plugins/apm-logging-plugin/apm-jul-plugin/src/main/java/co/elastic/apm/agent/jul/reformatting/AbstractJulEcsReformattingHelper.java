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
package co.elastic.apm.agent.jul.reformatting;

import co.elastic.apm.agent.jul.sending.JulLogSenderHandler;
import co.elastic.apm.agent.loginstr.correlation.CorrelationIdMapAdapter;
import co.elastic.apm.agent.loginstr.reformatting.AbstractEcsReformattingHelper;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.logging.AdditionalField;
import co.elastic.logging.jul.EcsFormatter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public abstract class AbstractJulEcsReformattingHelper<T extends Handler> extends AbstractEcsReformattingHelper<T, T, Formatter, LogRecord> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractJulEcsReformattingHelper.class);
    private static final Logger oneTimeLogFileLimitWarningLogger = LoggerUtils.logOnce(logger);

    @Nullable
    @Override
    protected Formatter getFormatterFrom(T handler) {
        return handler.getFormatter();
    }

    @Override
    protected void setFormatter(T handler, Formatter formatter) {
        handler.setFormatter(formatter);
    }

    @Override
    protected void closeShadeAppender(T handler) {
        handler.close();
    }

    @Nullable
    @Override
    public Formatter createEcsFormatter(String eventDataset,
                                        @Nullable String serviceName,
                                        @Nullable String serviceVersion,
                                        @Nullable String serviceEnvironment,
                                        @Nullable String serviceNodeName,
                                        @Nullable Map<String, String> additionalFields,
                                        @Nullable Formatter originalFormatter) {

        EcsFormatter ecsFormatter = new EcsFormatter() {
            @Override
            protected Map<String, String> getMdcEntries() {
                // using internal tracer state as ECS formatter is not instrumented within the agent plugin
                return CorrelationIdMapAdapter.get();
            }
        };
        ecsFormatter.setServiceName(serviceName);
        ecsFormatter.setServiceVersion(serviceVersion);
        ecsFormatter.setServiceEnvironment(serviceEnvironment);
        ecsFormatter.setServiceNodeName(serviceNodeName);
        ecsFormatter.setEventDataset(eventDataset);
        if (additionalFields != null && !additionalFields.isEmpty()) {
            List<AdditionalField> additionalFieldList = new ArrayList<>();
            for (Map.Entry<String, String> keyValuePair : additionalFields.entrySet()) {
                additionalFieldList.add(new AdditionalField(keyValuePair.getKey(), keyValuePair.getValue()));
            }
            ecsFormatter.setAdditionalFields(additionalFieldList);
        }
        ecsFormatter.setIncludeOrigin(false);
        ecsFormatter.setStackTraceAsArray(false);
        return ecsFormatter;
    }

    protected abstract String getShadeFilePatternAndCreateDir() throws IOException;

    @Nullable
    @Override
    protected Handler createAndStartEcsAppender(Handler originalHandler, String ecsAppenderName, Formatter ecsFormatter) {
        StreamHandler shadeHandler = null;
        if (isFileHandler(originalHandler)) {
            try {
                String pattern = getShadeFilePatternAndCreateDir();
                // In earlier versions, there is only constructor with log file limit given as int, whereas in later ones there are
                // overloads for both either int or long. Typically, this should be enough, but not necessarily
                int maxLogFileSize = (int) getMaxLogFileSize();
                if ((long) maxLogFileSize != getMaxLogFileSize()) {
                    maxLogFileSize = (int) getDefaultMaxLogFileSize();
                    oneTimeLogFileLimitWarningLogger.warn("Configured log max size ({} bytes) is too big for JUL settings, which " +
                        "use int to configure the file size limit. Consider reducing the log max size configuration to a value below " +
                        "Integer#MAX_VALUE. Defaulting to {} bytes.", getMaxLogFileSize(), maxLogFileSize);
                }
                shadeHandler = createFileHandler(pattern, maxLogFileSize);
                shadeHandler.setFormatter(ecsFormatter);
            } catch (Exception e) {
                logger.error("Failed to create Log shading FileAppender. Auto ECS reformatting will not work.", e);
            }
        }
        return shadeHandler;
    }

    private static FileHandler createFileHandler(final String pattern, final int maxLogFileSize) throws IOException {
        if (System.getSecurityManager() == null) {
            return doCreateFileHandler(pattern, maxLogFileSize);
        }
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<FileHandler>() {
                @Override
                public FileHandler run() throws Exception {
                    return doCreateFileHandler(pattern, maxLogFileSize);
                }
            });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static FileHandler doCreateFileHandler(String pattern, int maxLogFileSize) throws IOException {
        return new FileHandler(pattern, maxLogFileSize, 2, true);
    }

    protected abstract boolean isFileHandler(Handler originalHandler);

    @Override
    protected T createAndStartLogSendingAppender(Reporter reporter, Formatter formatter) {
        return (T) new JulLogSenderHandler(reporter, formatter);
    }

    @Override
    protected void append(LogRecord logEvent, Handler appender) {
        appender.publish(logEvent);
    }
}
