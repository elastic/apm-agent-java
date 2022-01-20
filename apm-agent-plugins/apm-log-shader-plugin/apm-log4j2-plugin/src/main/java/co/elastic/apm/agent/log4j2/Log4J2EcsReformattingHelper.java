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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.log.shader.AbstractEcsReformattingHelper;
import co.elastic.apm.agent.log.shader.Utils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.layout.AbstractLayout;
import org.apache.logging.log4j.core.util.KeyValuePair;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


class Log4J2EcsReformattingHelper extends AbstractEcsReformattingHelper<Appender, Layout<? extends Serializable>> {

    private static final Logger logger = LoggerFactory.getLogger(Log4J2EcsReformattingHelper.class);

    Log4J2EcsReformattingHelper() {}

    @Nullable
    @Override
    protected Layout<? extends Serializable> getFormatterFrom(Appender appender) {
        return appender.getLayout();
    }

    @Override
    protected String getAppenderName(Appender appender) {
        return appender.getName();
    }

    @Override
    protected Layout<? extends Serializable> createEcsFormatter(String eventDataset, @Nullable String serviceName, @Nullable String serviceNodeName,
                                                                @Nullable Map<String, String> additionalFields, Layout<? extends Serializable> originalFormatter) {
        EcsLayout.Builder builder = EcsLayout.newBuilder()
            .setServiceName(serviceName)
            .setServiceNodeName(serviceNodeName)
            .setEventDataset(eventDataset)
            .setIncludeMarkers(true)
            .setIncludeOrigin(false)
            .setStackTraceAsArray(false);

        if (originalFormatter instanceof AbstractLayout<?>) {
            builder.setConfiguration(((AbstractLayout<?>) originalFormatter).getConfiguration());
        } else {
            builder.setConfiguration(new NullConfiguration());
        }

        if (additionalFields != null && !additionalFields.isEmpty()) {
            List<KeyValuePair> additionalFieldsList = new ArrayList<KeyValuePair>();
            for (Map.Entry<String, String> keyValuePair : additionalFields.entrySet()) {
                additionalFieldsList.add(new KeyValuePair(keyValuePair.getKey(), keyValuePair.getValue()));
            }
            builder.setAdditionalFields(additionalFieldsList.toArray(new KeyValuePair[0]));
        }

        return builder.build();
    }

    @Override
    @Nullable
    protected Appender createAndStartEcsAppender(Appender originalAppender, String ecsAppenderName, Layout<? extends Serializable> ecsFormatter) {

        Appender ecsAppender = null;
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

        if (logFile != null) {
            String shadeFile = Utils.computeShadeLogFilePath(logFile, getConfiguredShadeDir());

            // The deprecated configuration API is used in order to support older versions where the Builder API is not yet available
            //noinspection deprecation
            RolloverStrategy rolloverStrategy = DefaultRolloverStrategy.createStrategy("1", "1", null,
                null, null, true, new DefaultConfiguration());

            TriggeringPolicy triggeringPolicy = SizeBasedTriggeringPolicy.createPolicy(String.valueOf(getMaxLogFileSize()));

            // The deprecated configuration API is used in order to support older versions where the Builder API is not yet available
            //noinspection deprecation
            ecsAppender = RollingRandomAccessFileAppender.createAppender(shadeFile, shadeFile + ".%i", "true",
                ecsAppenderName, String.valueOf(((AbstractOutputStreamAppender<?>) originalAppender).getImmediateFlush()),
                null, triggeringPolicy, rolloverStrategy, ecsFormatter, null, null, null, null, null);

            ecsAppender.start();
        }
        return ecsAppender;
    }

    @Override
    protected void setFormatter(Appender appender, Layout<? extends Serializable> layout) {
        // do nothing - log4j2 appenders do not provide an API to set the Layout. Instead, we override the original
        // layout though the instrumentation of getLayout() - see Log4j2AppenderGetLayoutAdvice
    }

    @Override
    protected void closeShadeAppender(Appender shadeAppender) {
        shadeAppender.stop();
    }
}
