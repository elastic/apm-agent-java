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

import co.elastic.apm.agent.loginstr.reformatting.AbstractEcsReformattingHelper;
import co.elastic.apm.agent.loginstr.reformatting.Utils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.logging.AdditionalField;
import co.elastic.logging.jul.EcsFormatter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.StreamHandler;

class JulEcsReformattingHelper extends AbstractEcsReformattingHelper<StreamHandler, Formatter> {

    private static final Logger logger = LoggerFactory.getLogger(JulEcsReformattingHelper.class);
    private static final Logger oneTimeLogFileLimitWarningLogger = LoggerUtils.logOnce(logger);

    private static final ThreadLocal<String> currentPattern = new ThreadLocal<>();
    private static final ThreadLocal<Path> currentExampleLogFile = new ThreadLocal<>();

    JulEcsReformattingHelper() {}

    public boolean onAppendEnter(FileHandler fileHandler, String pattern, File exampleLogFile) {
        try {
            currentPattern.set(pattern);
            currentExampleLogFile.set(exampleLogFile.toPath());
            return super.onAppendEnter(fileHandler);
        } finally {
            currentPattern.remove();
            currentExampleLogFile.remove();
        }
    }

    @Nullable
    @Override
    protected Formatter getFormatterFrom(StreamHandler handler) {
        return handler.getFormatter();
    }

    @Override
    protected void setFormatter(StreamHandler handler, Formatter formatter) {
        handler.setFormatter(formatter);
    }

    @Override
    protected String getAppenderName(StreamHandler handler) {
        if (handler instanceof FileHandler) {
            return "FILE";
        } else if (handler instanceof ConsoleHandler) {
            return "CONSOLE";
        } else {
            return handler.getClass().getSimpleName();
        }
    }

    @Override
    protected Formatter createEcsFormatter(String eventDataset, @Nullable String serviceName, @Nullable String serviceVersion,
                                           @Nullable String serviceNodeName, @Nullable Map<String, String> additionalFields,
                                           Formatter originalFormatter) {
        EcsFormatter ecsFormatter = new EcsFormatter();
        ecsFormatter.setServiceName(serviceName);
        ecsFormatter.setServiceVersion(serviceVersion);
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

    @Nullable
    @Override
    protected StreamHandler createAndStartEcsAppender(StreamHandler originalHandler, String ecsAppenderName, Formatter ecsFormatter) {
        StreamHandler shadeHandler = null;
        if (originalHandler instanceof FileHandler) {
            try {
                String pattern = computeEcsFileHandlerPattern(
                    currentPattern.get(),
                    currentExampleLogFile.get(),
                    getConfiguredReformattingDir(),
                    true
                );
                // In earlier versions, there is only constructor with log file limit given as int, whereas in later ones there are
                // overloads for both either int or long. Typically, this should be enough, but not necessarily
                int maxLogFileSize = (int) getMaxLogFileSize();
                if ((long) maxLogFileSize != getMaxLogFileSize()) {
                    maxLogFileSize = (int) getDefaultMaxLogFileSize();
                    oneTimeLogFileLimitWarningLogger.warn("Configured log max size ({} bytes) is too big for JUL settings, which " +
                        "use int to configure the file size limit. Consider reducing the log max size configuration to a value below " +
                        "Integer#MAX_VALUE. Defaulting to {} bytes.", getMaxLogFileSize(), maxLogFileSize);
                }
                shadeHandler = new FileHandler(pattern, maxLogFileSize, 2, true);
                shadeHandler.setFormatter(ecsFormatter);
            } catch (Exception e) {
                logger.error("Failed to create Log shading FileAppender. Auto ECS reformatting will not work.", e);
            }
        }
        return shadeHandler;
    }

    static String computeEcsFileHandlerPattern(String pattern, Path originalFilePath, @Nullable String configuredReformattingDir,
                                               boolean createDirs) throws IOException {
        pattern = Utils.replaceFileExtensionToEcsJson(pattern);
        // if the pattern does not contain rotation component, append one at the end
        if (!pattern.contains("%g")) {
            pattern = pattern + ".%g";
        }
        int lastPathSeparatorIndex = pattern.lastIndexOf('/');
        if (lastPathSeparatorIndex > 0 && pattern.length() > lastPathSeparatorIndex) {
            pattern = pattern.substring(lastPathSeparatorIndex + 1);
        }
        Path logReformattingDir = Utils.computeLogReformattingDir(originalFilePath, configuredReformattingDir);
        if (logReformattingDir != null) {
            if (createDirs && !Files.exists(logReformattingDir)) {
                Files.createDirectories(logReformattingDir);
            }
            pattern = logReformattingDir.resolve(pattern).toString();
        }
        return pattern;
    }

    @Override
    protected void closeShadeAppender(StreamHandler shadeHandler) {
        shadeHandler.close();
    }
}
