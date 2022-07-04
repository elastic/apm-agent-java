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

import co.elastic.apm.agent.sdk.logging.ILoggerFactory;
import co.elastic.apm.agent.sdk.logging.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.spi.AbstractLoggerAdapter;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.util.StackLocatorUtil;

/**
 * Based on {@code org.apache.logging.slf4j.Log4jLoggerFactory}.
 * <p>
 * This class does not implement {@link ILoggerFactory} directly but through the super class implementation that has
 * a method matching {@link ILoggerFactory#getLogger(String)}. Given this method is caller-sensitive then we can't
 * implement it directly without side effects.
 */
public class Log4jLoggerFactoryBridge extends AbstractLoggerAdapter<Logger> implements ILoggerFactory {

    private static final String LOGGER_FACTORY = "co.elastic.apm.agent.sdk.logging.LoggerFactory";

    public static void shutdown() {
        LoggerContextFactory factory = LogManager.getFactory();
        // the Spring tests use log4j-to-slf4j which uses SLF4JLoggerContextFactory which does not need cleanup
        if (factory instanceof Log4jContextFactory) {
            for (LoggerContext context : ((Log4jContextFactory) factory).getSelector().getLoggerContexts()) {
                LogManager.shutdown(context);
            }
        }
    }

    @Override
    protected Logger newLogger(final String name, final LoggerContext context) {
        final String key = Logger.ROOT_LOGGER_NAME.equals(name) ? LogManager.ROOT_LOGGER_NAME : name;
        return new Log4j2LoggerBridge(context.getLogger(key), name);
    }

    @Override
    protected LoggerContext getContext() {
        // the logger context is defined by the class that calls LoggerFactory
        final Class<?> factoryCaller = StackLocatorUtil.getCallerClass(LOGGER_FACTORY);
        if (factoryCaller == null) {
            return LogManager.getContext();
        }
        return getContext(factoryCaller);
    }

}
