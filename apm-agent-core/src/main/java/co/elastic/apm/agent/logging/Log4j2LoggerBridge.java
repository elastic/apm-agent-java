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

import java.security.AccessController;
import java.security.PrivilegedAction;

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
        return isEnabled(Level.TRACE);
    }

    @Override
    public void trace(final String format) {
        logIfEnabled(Level.TRACE, format);
    }

    @Override
    public void trace(final String format, final Object o) {
        logIfEnabled(Level.TRACE, format, o);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        logIfEnabled(Level.TRACE, format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... args) {
        logIfEnabled(Level.TRACE, format, args);
    }

    @Override
    public void trace(final String format, final Throwable t) {
        logIfEnabled(Level.TRACE, format, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    @Override
    public void debug(final String format) {
        logIfEnabled(Level.DEBUG, format);
    }

    @Override
    public void debug(final String format, final Object o) {
        logIfEnabled(Level.DEBUG, format, o);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        logIfEnabled(Level.DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... args) {
        logIfEnabled(Level.DEBUG, format, args);
    }

    @Override
    public void debug(final String format, final Throwable t) {
        logIfEnabled(Level.DEBUG, format, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    @Override
    public void info(final String format) {
        logIfEnabled(Level.INFO, format);
    }

    @Override
    public void info(final String format, final Object o) {
        logIfEnabled(Level.INFO, format, o);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        logIfEnabled(Level.INFO, format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... args) {
        logIfEnabled(Level.INFO, format, args);
    }

    @Override
    public void info(final String format, final Throwable t) {
        logIfEnabled(Level.INFO, format, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    @Override
    public void warn(final String format) {
        logIfEnabled(Level.WARN, format);
    }

    @Override
    public void warn(final String format, final Object o) {
        logIfEnabled(Level.WARN, format, o);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        logIfEnabled(Level.WARN, format, arg1, arg2);
    }

    @Override
    public void warn(final String format, final Object... args) {
        logIfEnabled(Level.WARN, format, args);
    }

    @Override
    public void warn(final String format, final Throwable t) {
        logIfEnabled(Level.WARN, format, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    @Override
    public void error(final String format) {
        logIfEnabled(Level.ERROR, format);
    }

    @Override
    public void error(final String format, final Object o) {
        logIfEnabled(Level.ERROR, format, o);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        logIfEnabled(Level.ERROR, format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... args) {
        logIfEnabled(Level.ERROR, format, args);
    }

    @Override
    public void error(final String format, final Throwable t) {
        logIfEnabled(Level.ERROR, format, t);
    }

    @SuppressWarnings("DuplicatedCode")
    private void logIfEnabled(final Level level, final String format) {
        if (System.getSecurityManager() == null) {
            log4jLogger.logIfEnabled(FQCN, level, null, format);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                log4jLogger.logIfEnabled(FQCN, level, null, format);
                return null;
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    private void logIfEnabled(final Level level, final String format, final Throwable t) {
        if (System.getSecurityManager() == null) {
            log4jLogger.logIfEnabled(FQCN, level, null, format, t);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                log4jLogger.logIfEnabled(FQCN, level, null, format, t);
                return null;
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    private void logIfEnabled(final Level level, final String format, final Object... params) {
        if (System.getSecurityManager() == null) {
            log4jLogger.logIfEnabled(FQCN, level, null, format, params);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                log4jLogger.logIfEnabled(FQCN, level, null, format, params);
                return null;
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    private void logIfEnabled(final Level level, final String format, final Object arg) {
        if (System.getSecurityManager() == null) {
            log4jLogger.logIfEnabled(FQCN, level, null, format, arg);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                log4jLogger.logIfEnabled(FQCN, level, null, format, arg);
                return null;
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    private void logIfEnabled(final Level level, final String format, final Object arg1, final Object arg2) {
        if (System.getSecurityManager() == null) {
            log4jLogger.logIfEnabled(FQCN, level, null, format, arg1, arg2);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                log4jLogger.logIfEnabled(FQCN, level, null, format, arg1, arg2);
                return null;
            }
        });
    }

    private boolean isEnabled(Level level) {
        return log4jLogger.isEnabled(level, null, null);
    }
}
