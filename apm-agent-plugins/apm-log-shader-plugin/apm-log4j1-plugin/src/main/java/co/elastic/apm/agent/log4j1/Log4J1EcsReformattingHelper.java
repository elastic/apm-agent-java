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
package co.elastic.apm.agent.log4j1;

import co.elastic.apm.agent.log.shader.AbstractEcsReformattingHelper;
import co.elastic.apm.agent.log.shader.Utils;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.logging.log4j.EcsLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.WriterAppender;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

class Log4J1EcsReformattingHelper extends AbstractEcsReformattingHelper<WriterAppender, Layout> {

    private static final Logger logger = LoggerFactory.getLogger(Log4J1EcsReformattingHelper.class);

    Log4J1EcsReformattingHelper() {}

    @Nullable
    @Override
    protected Layout getFormatterFrom(WriterAppender appender) {
        return appender.getLayout();
    }

    @Override
    protected void setFormatter(WriterAppender appender, Layout layout) {
        appender.setLayout(layout);
    }

    @Override
    protected String getAppenderName(WriterAppender appender) {
        return appender.getName();
    }

    @Override
    protected Layout createEcsFormatter(String eventDataset, @Nullable String serviceName, @Nullable String serviceNodeName,
                                        @Nullable Map<String, String> additionalFields, Layout originalFormatter) {
        EcsLayout ecsLayout = new EcsLayout();
        ecsLayout.setServiceName(serviceName);
        ecsLayout.setServiceNodeName(serviceNodeName);
        ecsLayout.setEventDataset(eventDataset);
        if (additionalFields != null) {
            for (Map.Entry<String, String> keyValuePair : additionalFields.entrySet()) {
                ecsLayout.setAdditionalField(keyValuePair.getKey() + "=" + keyValuePair.getValue());
            }
        }
        ecsLayout.setIncludeOrigin(false);
        ecsLayout.setStackTraceAsArray(false);
        return ecsLayout;
    }

    @Nullable
    @Override
    protected WriterAppender createAndStartEcsAppender(WriterAppender originalAppender, String ecsAppenderName, Layout ecsLayout) {
        RollingFileAppender shadeAppender = null;
        if (originalAppender instanceof FileAppender) {
            try {
                FileAppender fileAppender = (FileAppender) originalAppender;
                String shadeFile = Utils.computeShadeLogFilePath(fileAppender.getFile(), getConfiguredShadeDir());

                shadeAppender = new RollingFileAppender(ecsLayout, shadeFile, true);
                shadeAppender.setMaxBackupIndex(1);
                shadeAppender.setMaximumFileSize(getMaxLogFileSize());
                shadeAppender.setImmediateFlush(originalAppender.getImmediateFlush());
                shadeAppender.setName(ecsAppenderName);
                shadeAppender.setLayout(ecsLayout);
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
