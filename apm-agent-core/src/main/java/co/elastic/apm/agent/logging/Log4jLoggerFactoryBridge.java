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
import org.apache.logging.log4j.spi.AbstractLoggerAdapter;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.util.StackLocatorUtil;

/**
 * Based on {@code org.apache.logging.slf4j.Log4jLoggerFactory}
 */
public class Log4jLoggerFactoryBridge extends AbstractLoggerAdapter<Logger> implements ILoggerFactory {

    private static final String FQCN = Log4jLoggerFactoryBridge.class.getName();
    private static final String PACKAGE = "co.elastic.apm.agent.sdk.logging";

    @Override
    protected Logger newLogger(final String name, final LoggerContext context) {
        final String key = Logger.ROOT_LOGGER_NAME.equals(name) ? LogManager.ROOT_LOGGER_NAME : name;
        return new Log4j2LoggerBridge(context.getLogger(key), name);
    }

    @Override
    protected LoggerContext getContext() {
        final Class<?> anchor = StackLocatorUtil.getCallerClass(FQCN, PACKAGE);
        return anchor == null ? LogManager.getContext() : getContext(StackLocatorUtil.getCallerClass(anchor));
    }
}
