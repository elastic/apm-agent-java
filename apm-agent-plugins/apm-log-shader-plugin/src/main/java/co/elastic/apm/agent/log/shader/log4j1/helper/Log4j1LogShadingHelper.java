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
package co.elastic.apm.agent.log.shader.log4j1.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.log.shader.AbstractLogShadingHelper;
import co.elastic.apm.agent.log.shader.Utils;

import co.elastic.logging.log4j.EcsLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.WriterAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;


public class Log4j1LogShadingHelper extends AbstractLogShadingHelper<WriterAppender> {

    private static final Logger logger = LoggerFactory.getLogger(Log4j1LogShadingHelper.class);

    public Log4j1LogShadingHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    protected String getAppenderName(WriterAppender appender) {
        return appender.getName();
    }

    @Override
    @Nullable
    protected WriterAppender createAndConfigureAppender(WriterAppender originalAppender, String appenderName) {

        RollingFileAppender shadeAppender = null;
        if (originalAppender instanceof FileAppender) {
            try {
                FileAppender fileAppender = (FileAppender) originalAppender;
                String shadeFile = Utils.computeShadeLogFilePath(fileAppender.getFile());

                EcsLayout ecsLayout = new EcsLayout();
                ecsLayout.setServiceName(getServiceName());
                ecsLayout.setIncludeOrigin(false);
                ecsLayout.setStackTraceAsArray(false);

                shadeAppender = new RollingFileAppender(ecsLayout, shadeFile, true);
                shadeAppender.setMaxBackupIndex(1);
                shadeAppender.setMaximumFileSize(getMaxLogFileSize());
                shadeAppender.setImmediateFlush(originalAppender.getImmediateFlush());
                shadeAppender.setName(appenderName);
                shadeAppender.setLayout(ecsLayout);

                // todo - set rolling file pattern
            } catch (IOException e) {
                logger.error("Failed to create Log shading FileAppender. Auto ECS reformatting will not work.", e);
            }
        }
        return shadeAppender;
    }

    @Override
    protected void closeShadeAppender(WriterAppender shadeAppender) {
        shadeAppender.close();
    }
}
