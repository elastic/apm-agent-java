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
package co.elastic.apm.agent.tomcatlogging.reformatting;

import co.elastic.apm.agent.jul.reformatting.AbstractJulEcsReformattingHelper;
import co.elastic.apm.agent.loginstr.reformatting.Utils;
import org.apache.juli.FileHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Handler;

public class TomcatReformattingHelper extends AbstractJulEcsReformattingHelper<Handler> {

    private static final ThreadLocal<String> currentDirectory = new ThreadLocal<>();
    private static final ThreadLocal<String> currentPrefix = new ThreadLocal<>();
    private static final ThreadLocal<String> currentSuffix = new ThreadLocal<>();

    TomcatReformattingHelper() {
    }

    public boolean onAppendEnter(Handler fileHandler, String directory, String prefix, String suffix) {
        try {
            currentDirectory.set(directory);
            currentPrefix.set(prefix);
            currentSuffix.set(suffix);
            return super.onAppendEnter(fileHandler);
        } finally {
            currentDirectory.remove();
            currentPrefix.remove();
            currentSuffix.remove();
        }
    }

    @Nullable
    @Override
    protected String getAppenderName(Handler handler) {
        if (handler instanceof FileHandler) {
            return "FILE";
        }
        return handler.getClass().getSimpleName();
    }

    @Override
    protected String getShadeFilePatternAndCreateDir() throws IOException {

        StringBuilder originalFile = new StringBuilder(currentDirectory.get());
        if (originalFile.charAt(originalFile.length() - 1) != File.separatorChar) {
            originalFile.append(File.separatorChar);
        }
        originalFile.append(currentPrefix.get());
        originalFile.append(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        originalFile.append(currentSuffix.get());

        String pattern = Utils.replaceFileExtensionToEcsJson(originalFile.toString());
        pattern += ".%g";

        Path destinationPattern = Paths.get(pattern);
        Path destinationDir = Utils.computeLogReformattingDir(destinationPattern, getConfiguredReformattingDir());

        if (destinationDir != null) {
            if (!Files.exists(destinationDir)) {
                Files.createDirectories(destinationDir);
            }
            pattern = destinationDir.resolve(destinationPattern.getFileName()).toString();
        }

        return pattern;
    }

    @Override
    protected boolean isFileHandler(Handler originalHandler) {
        return originalHandler instanceof FileHandler;
    }

}
