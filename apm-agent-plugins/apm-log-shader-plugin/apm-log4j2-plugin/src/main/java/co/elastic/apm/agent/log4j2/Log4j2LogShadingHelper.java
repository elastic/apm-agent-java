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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.log.shader.AbstractLogShadingHelper;
import co.elastic.apm.agent.log.shader.Utils;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;


class Log4j2LogShadingHelper extends AbstractLogShadingHelper<AbstractOutputStreamAppender<?>> {

    private static final Logger logger = LoggerFactory.getLogger(Log4j2LogShadingHelper.class);

    private static final Log4j2LogShadingHelper INSTANCE = new Log4j2LogShadingHelper();

    static Log4j2LogShadingHelper instance() {
        return INSTANCE;
    }

    private Log4j2LogShadingHelper() {}

    @Override
    protected String getAppenderName(AbstractOutputStreamAppender<?> appender) {
        return appender.getName();
    }

    @Override
    @Nullable
    protected AbstractOutputStreamAppender<?> createAndConfigureAppender(AbstractOutputStreamAppender<?> originalAppender, String appenderName) {

        String logFile = null;

        // Using class names and reflection in order to avoid version sensitivity
        String appenderClassName = originalAppender.getClass().getName();
        if (appenderClassName.equals("org.apache.logging.log4j.core.appender.FileAppender") ||
            appenderClassName.equals("org.apache.logging.log4j.core.appender.RollingFileAppender") ||
            appenderClassName.equals("org.apache.logging.log4j.core.appender.RandomAccessFileAppender") ||
            appenderClassName.equals("org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender") ||
            appenderClassName.equals("org.apache.logging.log4j.core.appender.MemoryMappedFileAppender")) {
            try {
                Method getFileNameMethod = originalAppender.getClass().getDeclaredMethod("getFileName");
                logFile = (String) getFileNameMethod.invoke(originalAppender);
            } catch (Exception e) {
                logger.error("Failed to obtain log file name from file appender", e);
            }
        }

        if (logFile == null) {
            return null;
        }

        String shadeFile = Utils.computeShadeLogFilePath(logFile);

        EcsLayout ecsLayout = EcsLayout.newBuilder()
            .setServiceName(getServiceName())
            .setIncludeMarkers(false)
            .setIncludeOrigin(false)
            .setStackTraceAsArray(false)
            .build();

        // The deprecated configuration API is used in order to support older versions where the Builder API is not yet available
        RolloverStrategy rolloverStrategy = DefaultRolloverStrategy.createStrategy("1", "1", null,
            null, null,true, new DefaultConfiguration());

        TriggeringPolicy triggeringPolicy = SizeBasedTriggeringPolicy.createPolicy(String.valueOf(getMaxLogFileSize()));

        // The deprecated configuration API is used in order to support older versions where the Builder API is not yet available
        RollingRandomAccessFileAppender appender = RollingRandomAccessFileAppender.createAppender(shadeFile, shadeFile + ".%i",
            "true", appenderName, String.valueOf(originalAppender.getImmediateFlush()), null, triggeringPolicy,
            rolloverStrategy, ecsLayout, null, null, null, null, null);

        appender.start();
        return appender;
    }

    @Override
    protected void closeShadeAppender(AbstractOutputStreamAppender<?> shadeAppender) {
        shadeAppender.stop();
    }
}
