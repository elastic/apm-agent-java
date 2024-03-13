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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.tracer.AbstractLifecycleListener;
import co.elastic.apm.agent.tracer.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Objects;

@Plugin(name = Log4j2ConfigurationFactory.APM_SERVER_PLUGIN_NAME, category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class ApmServerLogAppender extends AbstractAppender {

    private static final int MAX_BUFFER_SIZE = 1024;

    @Nullable
    private static ApmServerLogAppender INSTANCE;

    @Nullable
    private volatile LoggingConfigurationImpl config;
    @Nullable
    private volatile Reporter reporter;

    private final ArrayList<LogEvent> buffer;

    // package protected for testing
    ApmServerLogAppender(String name, Layout<?> layout) {
        // recursive calls filtering is done through a filter on the appender ref, not on the appender itself
        super(name, null , layout, true, null);
        this.buffer = new ArrayList<>();
    }

    public static ApmServerLogAppender getInstance() {
        return Objects.requireNonNull(INSTANCE);
    }

    @SuppressWarnings("unused")
    @PluginFactory
    public static ApmServerLogAppender createAppender(@PluginAttribute("name") String name,
                                                      @PluginElement("Layout") Layout<?> layout) {

        if (!(layout instanceof EcsLayout)) {
            throw new IllegalArgumentException("invalid layout " + layout);
        }

        if (INSTANCE == null) {
            INSTANCE = new ApmServerLogAppender(name, layout);
        }

        return INSTANCE;
    }

    @Override
    public void append(LogEvent event) {

        boolean bufferBeforeInit = !isAgentInitialized();
        if (bufferBeforeInit) {
            synchronized (buffer) {
                bufferBeforeInit = !isAgentInitialized();

                // buffering before the configuration is known
                if (bufferBeforeInit) {
                    if (buffer.size() < MAX_BUFFER_SIZE) {
                        buffer.add(event.toImmutable());
                    }
                    return;
                }
            }
        }

        sendLogEvent(event);
    }

    public LifecycleListener getInitListener() {
        return new AbstractLifecycleListener() {
            @Override
            public void init(Tracer tracer) throws Exception {
                initStreaming(tracer.getConfig(LoggingConfigurationImpl.class), tracer.require(ElasticApmTracer.class).getReporter());
            }
        };
    }

    private void initStreaming(LoggingConfigurationImpl config, Reporter reporter) {
        if (isAgentInitialized()) {
            throw new IllegalStateException("streaming already initialized");
        }

        synchronized (buffer) {
            this.config = config;
            this.reporter = reporter;

            for (LogEvent logEvent : buffer) {
                sendLogEvent(logEvent);
            }
            buffer.clear();
            buffer.trimToSize();
        }
    }

    private void sendLogEvent(LogEvent event) {
        if (!config.getSendLogs()) {
            return;
        }
        Objects.requireNonNull(reporter).reportAgentLog(getLayout().toByteArray(event));
    }

    private boolean isAgentInitialized() {
        return this.config != null && this.reporter != null;
    }

}
