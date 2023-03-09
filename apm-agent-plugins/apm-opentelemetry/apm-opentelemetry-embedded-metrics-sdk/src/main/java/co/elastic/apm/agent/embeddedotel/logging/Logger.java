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
package co.elastic.apm.agent.embeddedotel.logging;

import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.logging.Level;

/**
 * The otel SDK internally uses java.util.logging.Logger.
 * Those usages will be shaded to use this class instead.
 */
public class Logger {

    private enum TranslatedLevel {
        ERROR, WARN, INFO, DEBUG, TRACE
    }

    private final co.elastic.apm.agent.sdk.logging.Logger delegate;

    private Logger(co.elastic.apm.agent.sdk.logging.Logger delegate) {
        this.delegate = delegate;
    }

    public static Logger getLogger(String name) {
        co.elastic.apm.agent.sdk.logging.Logger delegate = LoggerFactory.getLogger(name);
        return new Logger(delegate);
    }

    public static Logger getLogger(String name, String resourceBundleName) {
        return getLogger(name);
    }


    public void log(final Level level, final String msg) {
        switch (mapLevel(level)) {
            case ERROR:
                delegate.error(msg);
                break;
            case WARN:
                delegate.warn(msg);
                break;
            case INFO:
                delegate.info(msg);
                break;
            case DEBUG:
                delegate.debug(msg);
                break;
            case TRACE:
                delegate.trace(msg);
                break;
        }
    }

    public void log(final Level level, final String msg, final Object param1) {
        switch (mapLevel(level)) {
            case ERROR:
                delegate.error(msg, param1);
                break;
            case WARN:
                delegate.warn(msg, param1);
                break;
            case INFO:
                delegate.info(msg, param1);
                break;
            case DEBUG:
                delegate.debug(msg, param1);
                break;
            case TRACE:
                delegate.trace(msg, param1);
                break;
        }
    }

    public void log(final Level level, final String msg, final Object[] params) {
        switch (mapLevel(level)) {
            case ERROR:
                delegate.error(msg, params);
                break;
            case WARN:
                delegate.warn(msg, params);
                break;
            case INFO:
                delegate.info(msg, params);
                break;
            case DEBUG:
                delegate.debug(msg, params);
                break;
            case TRACE:
                delegate.trace(msg, params);
                break;
        }
    }

    public void log(final Level level, final String msg, final Throwable thrown) {
        switch (mapLevel(level)) {
            case ERROR:
                delegate.error(msg, thrown);
                break;
            case WARN:
                delegate.warn(msg, thrown);
                break;
            case INFO:
                delegate.info(msg, thrown);
                break;
            case DEBUG:
                delegate.debug(msg, thrown);
                break;
            case TRACE:
                delegate.trace(msg, thrown);
                break;
        }
    }

    public void severe(final String msg) {
        log(Level.SEVERE, msg);
    }

    public void warning(final String msg) {
        log(Level.WARNING, msg);
    }

    public void info(final String msg) {
        log(Level.INFO, msg);
    }

    public void config(final String msg) {
        log(Level.CONFIG, msg);
    }

    public void fine(final String msg) {
        log(Level.FINE, msg);
    }

    public void finer(final String msg) {
        log(Level.FINER, msg);
    }

    public void finest(final String msg) {
        log(Level.FINEST, msg);
    }

    public void setLevel(final Level newLevel) throws SecurityException {
    }

    public boolean isLoggable(final Level level) {
        switch (mapLevel(level)) {
            case ERROR:
                return delegate.isErrorEnabled();
            case WARN:
                return delegate.isWarnEnabled();
            case INFO:
                return delegate.isInfoEnabled();
            case DEBUG:
                return delegate.isDebugEnabled();
            case TRACE:
                return delegate.isTraceEnabled();
            default:
                return false;
        }
    }


    private static TranslatedLevel mapLevel(final Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return TranslatedLevel.ERROR;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return TranslatedLevel.WARN;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return TranslatedLevel.INFO;
        } else if (level.intValue() >= Level.FINE.intValue()) {
            return TranslatedLevel.DEBUG;
        } else {
            return TranslatedLevel.TRACE;
        }
    }
}
