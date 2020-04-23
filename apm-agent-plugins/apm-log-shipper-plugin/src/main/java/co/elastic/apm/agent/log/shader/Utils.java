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

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    static final String SHADE_LOGS_DIR_NAME = "shade_logs";

    /**
     * Computes a shade log file path based on a given log file. The shade log file will have the same name as the
     * original log file and will be located in a subdirectory of the original file's parent directory.
     * @param originalLogFile the log file to which a shade file path is required
     * @return the shade log file path
     */
    public static String computeShadeLogFilePath(String originalLogFile) {
        Path originalFilePath = Paths.get(originalLogFile);
        Path logFileName = originalFilePath.getFileName();
        Path logFileDir = originalFilePath.getParent();
        Path shadeDir;
        if (logFileDir != null) {
            shadeDir = logFileDir.resolve(SHADE_LOGS_DIR_NAME);
        } else {
            shadeDir = Paths.get(SHADE_LOGS_DIR_NAME);
        }
        return shadeDir.resolve(logFileName).toString();
    }
}
