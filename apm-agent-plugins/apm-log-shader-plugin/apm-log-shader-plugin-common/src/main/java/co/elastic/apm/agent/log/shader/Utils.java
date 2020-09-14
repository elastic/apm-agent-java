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
package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.logging.LoggingConfiguration;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    private static final String SHADE_FILE_EXTENSION = ".ecs.json";
    private static final LoggingConfiguration config = GlobalTracer.requireTracerImpl().getConfig(LoggingConfiguration.class);

    /**
     * Computes a shade log file path based on a given log file. The shade log file will have the same name as the
     * original log file, but with the ".ecs.json" extension. Depending on configuration, it will be located in
     * the same directory alongside the original logs, or in an alternative directory.
     *
     * @param originalLogFile the log file to which a shade file path is required
     * @return the shade log file path
     */
    public static String computeShadeLogFilePath(String originalLogFile) {
        Path originalFilePath = Paths.get(originalLogFile);
        Path logFileName = originalFilePath.getFileName();
        if (!config.logShadingOverrideOriginalLogFiles()) {
            logFileName = Paths.get(replaceFileExtensionToEcsJson(logFileName.toString()));
        }
        Path shadeDir = originalFilePath.getParent();
        String configuredShadeFileDestinationDir = config.getLogShadingDestinationDir();
        if (configuredShadeFileDestinationDir != null) {
            shadeDir = Paths.get(configuredShadeFileDestinationDir);
        }
        if (shadeDir != null) {
            logFileName = shadeDir.resolve(logFileName);
        }
        return logFileName.toString();
    }

    static String replaceFileExtensionToEcsJson(String originalFileName) {
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            originalFileName = originalFileName.substring(0, extensionIndex);
        }
        return originalFileName.concat(SHADE_FILE_EXTENSION);
    }
}
