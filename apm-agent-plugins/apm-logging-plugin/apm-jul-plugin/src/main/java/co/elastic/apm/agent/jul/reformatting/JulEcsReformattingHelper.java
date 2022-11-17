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

import co.elastic.apm.agent.loginstr.reformatting.Utils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;

class JulEcsReformattingHelper extends AbstractJulEcsReformattingHelper {

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

    @Override
    protected String getAppenderName(Handler handler) {
        if (handler instanceof FileHandler) {
            return "FILE";
        } else if (handler instanceof ConsoleHandler) {
            return "CONSOLE";
        } else {
            return handler.getClass().getSimpleName();
        }
    }

    @Override
    protected boolean isFileHandler(Handler originalHandler) {
        return originalHandler instanceof FileHandler;
    }

    @Override
    protected String getShadeFilePatternAndCreateDir() throws IOException {
        return computeEcsFileHandlerPattern(
            currentPattern.get(),
            currentExampleLogFile.get(),
            getConfiguredReformattingDir(),
            true
        );
    }

    static String computeEcsFileHandlerPattern(String pattern, Path originalFilePath, @Nullable String configuredReformattingDir,
                                               boolean createDirs) throws IOException {
        pattern = Utils.replaceFileExtensionToEcsJson(pattern);
        // if the pattern does not contain rotation component, append one at the end
        if (!pattern.contains("%g")) {
            pattern = pattern + ".%g";
        }
        int lastPathSeparatorIndex = pattern.lastIndexOf('/');
        if (lastPathSeparatorIndex > 0 && lastPathSeparatorIndex < pattern.length() - 1) {
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

}
