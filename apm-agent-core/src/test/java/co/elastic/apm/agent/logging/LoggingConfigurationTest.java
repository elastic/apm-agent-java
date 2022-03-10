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

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.logging.instr.LoggerTestInstrumentation;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.selector.ContextSelector;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_LEVEL_KEY;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NotNullFieldNotInitialized")
class LoggingConfigurationTest {

    public static final String LOGGING_TEST_FILE = "target/LoggingConfigurationTest.log";

    private static LoggerContextFactory originalLoggerContextFactory;
    private static TestLog4jContextFactory testLog4jContextFactory;

    private static ConfigurationOption<LogLevel> logLevelConfig;

    private static Logger agentLogger;
    private static org.slf4j.Logger configOptionLogger;
    private static Logger pluginLogger;

    @Nullable
    private static String originalLogFile;
    @Nullable
    private static String originalLogFileFormat;

    @BeforeAll
    static void setup() throws NoSuchFieldException, IllegalAccessException, IOException {
        ElasticApmTracer tracer = MockTracer.createRealTracer();

        ConfigurationRegistry configurationRegistry = tracer.getConfigurationRegistry();
        //noinspection unchecked
        logLevelConfig = (ConfigurationOption<LogLevel>) configurationRegistry.getConfigurationOptionByKey(LOG_LEVEL_KEY);
        originalLogFile = System.getProperty("elastic.apm.log_file");
        originalLogFileFormat = System.getProperty("elastic.apm.log_format_file");
        Files.deleteIfExists(Path.of(LOGGING_TEST_FILE));
        System.setProperty("elastic.apm.log_file", LOGGING_TEST_FILE);
        System.setProperty("elastic.apm.log_format_file", "JSON");
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), List.of(new LoggerTestInstrumentation()));

        // We need to clean the current contexts for this test to resemble early agent setup
        originalLoggerContextFactory = LogManager.getFactory();
        // Setting with a custom context factory that create a new context for each ClassLoader. This is similar to the default
        // context log4j2 context factory, with one exception - this simple factory doesn't consider ClassLoader hierarchy
        // as the context, but each ClassLoader is consider a different context.
        // This better reflects the runtime environment of real agent, where the agent CL is not part of the CL hierarchy
        // of plugin class loaders (it's contents are available, but it is not part of the inherent CL hierarchy)
        testLog4jContextFactory = new TestLog4jContextFactory();
        LogManager.setFactory(testLog4jContextFactory);

        // Testing that stagemonitor's slf4j loggers are setup properly and get the agent configuration
        Field configOptionLoggerField = ConfigurationOption.class.getDeclaredField("logger");
        configOptionLoggerField.setAccessible(true);
        configOptionLogger = (org.slf4j.Logger) configOptionLoggerField.get(ConfigurationOption.booleanOption().buildOptional());

        // Not really an agent logger, but representing the agent-level logger
        agentLogger = LoggerFactory.getLogger(LoggingConfigurationTest.class);

        // A logger created by a plugin CL - see LoggerTestInstrumentation
        pluginLogger = Objects.requireNonNull(new LoggerTest().getLogger());
    }

    @AfterAll
    static void reset() {
        if (originalLogFile == null) {
            System.clearProperty("elastic.apm.log_file");
        } else {
            System.setProperty("elastic.apm.log_file", originalLogFile);
        }
        if (originalLogFileFormat == null) {
            System.clearProperty("elastic.apm.log_format_file");
        } else {
            System.setProperty("elastic.apm.log_format_file", originalLogFileFormat);
        }

        LogManager.setFactory(originalLoggerContextFactory);
        ElasticApmAgent.reset();
    }

    @Test
    void loggingLevelChangeTest() throws IOException {
        // Assuming default is debug level in tests based on test.elasticapm.properties
        assertThat(agentLogger.isTraceEnabled()).isFalse();
        LoggerContext agentLoggerContext = testLog4jContextFactory.getContext(agentLogger.getName());

        // stagemonitor relies on slf4j, which should be bridged to the same log4j registries and get the same configuration
        assertThat(configOptionLogger.isTraceEnabled()).isFalse();
        assertThat(testLog4jContextFactory.getContext(configOptionLogger.getName())).isEqualTo(agentLoggerContext);

        LoggerContext pluginLoggerContext = testLog4jContextFactory.getContext(pluginLogger.getName());
        assertThat(pluginLoggerContext).isNotNull();
        assertThat(pluginLoggerContext).isNotEqualTo(agentLoggerContext);
        assertThat(pluginLoggerContext.getName()).startsWith(IndyPluginClassLoader.class.getName());
        assertThat(pluginLogger.isTraceEnabled()).isFalse();

        logLevelConfig.update(LogLevel.TRACE, SpyConfiguration.CONFIG_SOURCE_NAME);
        assertThat(agentLogger.isTraceEnabled()).isTrue();
        assertThat(configOptionLogger.isTraceEnabled()).isTrue();
        assertThat(pluginLogger.isTraceEnabled()).isTrue();
    }

    @Test
    void testFileLogging() throws IOException {
        agentLogger.info("agent");
        configOptionLogger.info("config");
        pluginLogger.info("plugin");
        ArrayList<JsonNode> logJsonLines = TestUtils.readJsonFile(LOGGING_TEST_FILE);
        assertThat(logJsonLines).hasSize(3);
        for (JsonNode logJsonLine : logJsonLines) {
            assertThat(logJsonLine.get("@timestamp")).isNotNull();
            assertThat(logJsonLine.get("process.thread.name").textValue()).isEqualTo("main");
            assertThat(logJsonLine.get("log.level").textValue()).isEqualTo("INFO");
            assertThat(logJsonLine.get("log.logger")).isNotNull();
            assertThat(logJsonLine.get("message")).isNotNull();
            assertThat(logJsonLine.get("ecs.version")).isNotNull();
        }
    }

    private static class LoggerTest {
        @Nullable
        Logger getLogger() {
            return null;
        }
    }

    private static final class TestLog4jContextFactory extends Log4jContextFactory {

        ContextSelector contextSelector = new TestContextSelector();

        @Nullable
        LoggerContext getContext(String loggerName) {
            for (LoggerContext loggerContext : contextSelector.getLoggerContexts()) {
                for (org.apache.logging.log4j.core.Logger log4jLogger : loggerContext.getLoggers()) {
                    if (log4jLogger.getName().equals(loggerName)) {
                        return loggerContext;
                    }
                }
            }
            return null;
        }

        @Override
        public LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext) {
            return contextSelector.getContext(fqcn, loader, currentContext);
        }

        @Override
        public LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext, URI configLocation, String name) {
            return getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext, ConfigurationSource source) {
            return getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext, Configuration configuration) {
            return getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext, List<URI> configLocations, String name) {
            return getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public ContextSelector getSelector() {
            return contextSelector;
        }

        @Override
        public void removeContext(org.apache.logging.log4j.spi.LoggerContext context) {
            if (context instanceof LoggerContext) {
                contextSelector.removeContext((LoggerContext) context);
            }
        }

        private static class TestContextSelector implements ContextSelector {
            private final Map<ClassLoader, LoggerContext> contextMap = new HashMap<>();

            @Override
            public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext) {
                if (loader == null) {
                    loader = ClassLoader.getSystemClassLoader();
                }
                LoggerContext loggerContext = contextMap.get(loader);
                if (loggerContext == null) {
                    org.apache.logging.log4j.core.LoggerContext loggerContextImpl = new org.apache.logging.log4j.core.LoggerContext(Objects.toString(loader));
                    // This mimics the actual mechanism - configuration will be applied here
                    loggerContextImpl.start();
                    loggerContext = loggerContextImpl;
                    contextMap.put(loader, loggerContext);
                }
                return loggerContext;
            }

            @Override
            public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext, URI configLocation) {
                return getContext(fqcn, loader, currentContext);
            }

            @Override
            public List<LoggerContext> getLoggerContexts() {
                return List.copyOf(contextMap.values());
            }

            @Override
            public void removeContext(LoggerContext context) {
                contextMap.values().removeIf(curr -> curr.equals(context));
            }
        }
    }
}
