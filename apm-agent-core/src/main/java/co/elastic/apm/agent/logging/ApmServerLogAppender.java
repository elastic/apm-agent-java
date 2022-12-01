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

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Plugin(name = "ApmServer", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class ApmServerLogAppender extends AbstractAppender {

    public static final int MAX_BUFFER_SIZE = 1024;

    @Nullable
    private static ApmServerLogAppender INSTANCE;

    @Nullable
    private LoggingConfiguration config;
    @Nullable
    private Reporter reporter;

    private final List<LogEvent> buffer;

    private ApmServerLogAppender(String name, Layout<?> layout) {
        super(name, null, layout, true, null);
        this.buffer = new ArrayList<>();
    }

    public static ApmServerLogAppender getInstance() {
        return Objects.requireNonNull(INSTANCE);
    }

    @SuppressWarnings("unused")
    @PluginFactory
    public static ApmServerLogAppender createAppender(@PluginAttribute("name") String name,
                                                      @PluginElement("Layout") Layout<?> layout) {

        if(!(layout instanceof EcsLayout)){
            throw new IllegalArgumentException("invalid layout "+ layout);
        }

        if (INSTANCE == null) {
            INSTANCE = new ApmServerLogAppender(name, layout);
        }

        return INSTANCE;
    }

    @Override
    public void append(LogEvent event) {
        if (!isAgentInitialized()) {
            synchronized (buffer) {
                if (buffer.size() < MAX_BUFFER_SIZE) {
                    buffer.add(event.toImmutable());
                }
            }
            return;
        }

        if (!config.getSendLogs()) {
            return;
        }
        sendLogEvent(event);
    }

    public LifecycleListener getInitListener() {
        return new AbstractLifecycleListener() {
            @Override
            public void init(ElasticApmTracer tracer) throws Exception {
                initStreaming(tracer.getConfig(LoggingConfiguration.class), tracer.getReporter());
            }
        };
    }

    private void initStreaming(LoggingConfiguration config, Reporter reporter) {
        if (isAgentInitialized()) {
            throw new IllegalStateException("streaming already initialized");
        }

        this.config = config;
        this.reporter = reporter;

        synchronized (buffer) {
            for (LogEvent logEvent : buffer) {
                sendLogEvent(logEvent);
            }
            buffer.clear();
        }
    }


    private void sendLogEvent(LogEvent event) {
        // When trying to debug wire protocol, adding the whole HTTP request body nested in a log message is not possible
        // otherwise it makes the agent recursively nest payload data until the request size limit is reached.
        //
        // We have to filter this exception here to still provide the ability to log to filesystem if needed.
        if (event.getLevel().intLevel() >= Level.TRACE.intLevel() && event.getLoggerName().equals(DslJsonSerializer.class.getName())) {
            MutableLogEvent newEvent = new MutableLogEvent();
            newEvent.initFrom(event);
            newEvent.setMessage(new SimpleMessage("wire protocol logging only available when logging to filesystem"));
            event = newEvent;
        }

        Objects.requireNonNull(reporter).reportAgentLog(getLayout().toByteArray(event));
    }

    private boolean isAgentInitialized() {
        return this.config != null && this.reporter != null;
    }
}
