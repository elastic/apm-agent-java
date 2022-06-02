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

import co.elastic.apm.agent.sdk.logging.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.ExtendedLogger;

/**
 * Based on {@code org.apache.logging.slf4j.Log4jLogger}
 */
public class Log4j2LoggerBridge implements Logger {

    public static final String FQCN = Log4j2LoggerBridge.class.getName();

    private final ExtendedLogger log4jLogger;
    private final String name;

    public Log4j2LoggerBridge(ExtendedLogger log4jLogger, String name) {
        this.log4jLogger = log4jLogger;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return log4jLogger.isEnabled(Level.TRACE, null, null);
    }

    @Override
    public void trace(final String format) {
        log4jLogger.logIfEnabled(FQCN, Level.TRACE, null, format);
    }

    @Override
    public void trace(final String format, final Object o) {
        log4jLogger.logIfEnabled(FQCN, Level.TRACE, null, format, o);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        log4jLogger.logIfEnabled(FQCN, Level.TRACE, null, format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... args) {
        log4jLogger.logIfEnabled(FQCN, Level.TRACE, null, format, args);
    }

    @Override
    public void trace(final String format, final Throwable t) {
        log4jLogger.logIfEnabled(FQCN, Level.TRACE, null, format, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return log4jLogger.isEnabled(Level.DEBUG, null, null);
    }

    @Override
    public void debug(final String format) {
        log4jLogger.logIfEnabled(FQCN, Level.DEBUG, null, format);
    }

    @Override
    public void debug(final String format, final Object o) {
        log4jLogger.logIfEnabled(FQCN, Level.DEBUG, null, format, o);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        log4jLogger.logIfEnabled(FQCN, Level.DEBUG, null, format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... args) {
        log4jLogger.logIfEnabled(FQCN, Level.DEBUG, null, format, args);
    }

    @Override
    public void debug(final String format, final Throwable t) {
        log4jLogger.logIfEnabled(FQCN, Level.DEBUG, null, format, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return log4jLogger.isEnabled(Level.INFO, null, null);
    }

    @Override
    public void info(final String format) {
        log4jLogger.logIfEnabled(FQCN, Level.INFO, null, format);
    }

    @Override
    public void info(final String format, final Object o) {
        log4jLogger.logIfEnabled(FQCN, Level.INFO, null, format, o);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        log4jLogger.logIfEnabled(FQCN, Level.INFO, null, format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... args) {
        log4jLogger.logIfEnabled(FQCN, Level.INFO, null, format, args);
    }

    @Override
    public void info(final String format, final Throwable t) {
        log4jLogger.logIfEnabled(FQCN, Level.INFO, null, format, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return log4jLogger.isEnabled(Level.WARN, null, null);
    }

    @Override
    public void warn(final String format) {
        log4jLogger.logIfEnabled(FQCN, Level.WARN, null, format);
    }

    @Override
    public void warn(final String format, final Object o) {
        log4jLogger.logIfEnabled(FQCN, Level.WARN, null, format, o);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        log4jLogger.logIfEnabled(FQCN, Level.WARN, null, format, arg1, arg2);
    }

    @Override
    public void warn(final String format, final Object... args) {
        log4jLogger.logIfEnabled(FQCN, Level.WARN, null, format, args);
    }

    @Override
    public void warn(final String format, final Throwable t) {
        log4jLogger.logIfEnabled(FQCN, Level.WARN, null, format, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return log4jLogger.isEnabled(Level.ERROR, null, null);
    }

    @Override
    public void error(final String format) {
        log4jLogger.logIfEnabled(FQCN, Level.ERROR, null, format);
    }

    @Override
    public void error(final String format, final Object o) {
        log4jLogger.logIfEnabled(FQCN, Level.ERROR, null, format, o);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        log4jLogger.logIfEnabled(FQCN, Level.ERROR, null, format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... args) {
        log4jLogger.logIfEnabled(FQCN, Level.ERROR, null, format, args);
    }

    @Override
    public void error(final String format, final Throwable t) {
        log4jLogger.logIfEnabled(FQCN, Level.ERROR, null, format, t);
    }
}
