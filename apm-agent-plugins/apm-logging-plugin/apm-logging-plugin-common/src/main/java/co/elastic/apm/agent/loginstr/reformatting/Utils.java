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
package co.elastic.apm.agent.loginstr.reformatting;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    private static final String SHADE_FILE_EXTENSION = ".ecs.json";

    /**
     * Computes a shade log file path based on a given log file. The shade log file will have the same name as the
     * original log file, but with the ".ecs.json" extension. Depending on configuration, it will be located in
     * the same directory alongside the original logs, or in an alternative directory.
     *
     * @param originalLogFile the log file to which a shade file path is required
     * @return the shade log file path
     */
    public static String computeLogReformattingFilePath(String originalLogFile, @Nullable String configuredReformattingDestinationDir) {
        Path originalFilePath = Paths.get(originalLogFile);
        Path logFileName = Paths.get(replaceFileExtensionToEcsJson(originalFilePath.getFileName().toString()));
        Path reformattingDir = computeLogReformattingDir(originalFilePath, configuredReformattingDestinationDir);
        if (reformattingDir != null) {
            logFileName = reformattingDir.resolve(logFileName);
        }
        return logFileName.toString();
    }

    @Nullable
    private static Path computeLogReformattingDir(Path originalFilePath, @Nullable String configuredReformattingDestinationDir) {
        Path shadeDir;
        Path logsDir = originalFilePath.getParent();
        if (configuredReformattingDestinationDir == null) {
            shadeDir = logsDir;
        } else {
            shadeDir = Paths.get(configuredReformattingDestinationDir);
            if (!shadeDir.isAbsolute() && logsDir != null) {
                    shadeDir = logsDir.resolve(shadeDir);
            }
        }
        return shadeDir;
    }

    static String replaceFileExtensionToEcsJson(String originalFileName) {
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            originalFileName = originalFileName.substring(0, extensionIndex);
        }
        return originalFileName.concat(SHADE_FILE_EXTENSION);
    }
}
