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
package co.elastic.apm.agent.log.shader.log4j2.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.log.shader.AbstractLogShadingHelper;
import co.elastic.apm.agent.log.shader.Utils;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.MemoryMappedFileAppender;
import org.apache.logging.log4j.core.appender.RandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.DirectWriteRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import javax.annotation.Nullable;


public class Log4j2LogShadingHelper extends AbstractLogShadingHelper<AbstractOutputStreamAppender<?>> {

    public static final String ECS_SHADE_APPENDER = "EcsShadeAppender";

    public Log4j2LogShadingHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    protected boolean isShadingAppender(AbstractOutputStreamAppender<?> appender) {
        //noinspection StringEquality
        return appender.getName() == ECS_SHADE_APPENDER;
    }

    @Override
    @Nullable
    protected AbstractOutputStreamAppender<?> createAndConfigureAppender(AbstractOutputStreamAppender<?> originalAppender) {

        String logFile;
        if (originalAppender instanceof FileAppender) {
            logFile = ((FileAppender) originalAppender).getFileName();
        } else if (originalAppender instanceof RollingFileAppender) {
            logFile = ((RollingFileAppender) originalAppender).getFileName();
        } else if (originalAppender instanceof RandomAccessFileAppender) {
            logFile = ((RandomAccessFileAppender) originalAppender).getFileName();
        } else if (originalAppender instanceof RollingRandomAccessFileAppender) {
            logFile = ((RollingRandomAccessFileAppender) originalAppender).getFileName();
        } else if (originalAppender instanceof MemoryMappedFileAppender) {
            logFile = ((MemoryMappedFileAppender) originalAppender).getFileName();
        } else {
            return null;
        }

        String shadeFile = Utils.computeShadeLogFilePath(logFile);

        Configuration config = new DefaultConfiguration();

        EcsLayout ecsLayout = EcsLayout.newBuilder()
            .setServiceName(getServiceName())
            .setIncludeMarkers(false)
            .setIncludeOrigin(false)
            .setStackTraceAsArray(true)
            .build();

        RolloverStrategy rolloverStrategy = DefaultRolloverStrategy.newBuilder()
            .withMin("1")
            .withMax("1")
            .build();

        TriggeringPolicy triggeringPolicy = SizeBasedTriggeringPolicy.createPolicy(String.valueOf(getMaxLogFileSize()));

        RollingRandomAccessFileAppender appender = RollingRandomAccessFileAppender.newBuilder()
            .withFileName(shadeFile)
            .withFilePattern(shadeFile + ".%i")
            .withImmediateFlush(originalAppender.getImmediateFlush())
            .withStrategy(rolloverStrategy)
            .withPolicy(triggeringPolicy)
            .withAppend(true)
            .setName(ECS_SHADE_APPENDER)
            .setLayout(ecsLayout)
            .build();

        appender.start();
        return appender;
    }

    @Override
    protected void closeShadeAppender(AbstractOutputStreamAppender<?> shadeAppender) {
        shadeAppender.stop();
    }
}
