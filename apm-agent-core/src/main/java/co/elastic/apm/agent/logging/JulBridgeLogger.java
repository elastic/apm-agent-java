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
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.ResourceBundle;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
 * <p>
 * Some dependencies of the agent like okhttp use {@link java.util.logging.Logger}.
 * This is problematic,
 * as calling {@link java.util.logging.Logger#getLogger(String)} initializes the default {@link java.util.logging.LogManager}.
 * WildFly expects the {@link java.util.logging.LogManager} implementation to be {@code org.jboss.logmanager.LogManager} and refuses to
 * start otherwise.
 * There are workarounds (https://docs.tcell.io/docs/jboss-issue-with-external-java-agent)
 * but it would require users of the agent to configure some obscure stuff.
 * </p>
 * <p>
 * The approach we take is to mimic the public interface of {@link java.util.logging.Logger} and use the maven shadow plugin to replace the
 * usage of {@link java.util.logging.Logger} in our dependencies with this class.
 * This class then forwards the logger calls to a {@link co.elastic.apm.agent.sdk.logging.Logger}.
 * </p>
 */
public class JulBridgeLogger {

    public static final String GLOBAL_LOGGER_NAME = "global";
    public static final JulBridgeLogger global = new JulBridgeLogger(LoggerFactory.getLogger(GLOBAL_LOGGER_NAME));
    private final Logger logger;

    JulBridgeLogger(Logger logger) {
        this.logger = logger;
    }

    public static JulBridgeLogger getGlobal() {
        return global;
    }

    public static JulBridgeLogger getLogger(String name) {
        return new JulBridgeLogger(LoggerFactory.getLogger(name));
    }

    public static JulBridgeLogger getLogger(String name, String resourceBundleName) {
        return getLogger(name);
    }

    public static JulBridgeLogger getAnonymousLogger() {
        return global;
    }

    public static JulBridgeLogger getAnonymousLogger(String resourceBundleName) {
        return global;
    }

    public String getName() {
        return logger.getName();
    }

    public void severe(String msg) {
        logger.error(msg);
    }

    public void warning(String msg) {
        logger.warn(msg);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void config(String msg) {
        logger.info(msg);
    }

    public void fine(String msg) {
        logger.debug(msg);
    }

    public void finer(String msg) {
        logger.debug(msg);
    }

    public void finest(String msg) {
        logger.trace(msg);
    }

    public void log(Level level, String msg) {
        if (level == SEVERE) {
            logger.error(msg);
        } else if (level == WARNING) {
            logger.warn(msg);
        } else if (level == INFO) {
            logger.info(msg);
        } else if (level == CONFIG) {
            logger.info(msg);
        } else if (level == FINE) {
            logger.debug(msg);
        } else if (level == FINER) {
            logger.debug(msg);
        } else if (level == FINEST) {
            logger.trace(msg);
        }
    }

    public void log(Level level, String msg, Throwable thrown) {
        if (level == SEVERE) {
            logger.error(msg, thrown);
        } else if (level == WARNING) {
            logger.warn(msg, thrown);
        } else if (level == INFO) {
            logger.info(msg, thrown);
        } else if (level == CONFIG) {
            logger.info(msg, thrown);
        } else if (level == FINE) {
            logger.debug(msg, thrown);
        } else if (level == FINER) {
            logger.debug(msg, thrown);
        } else if (level == FINEST) {
            logger.trace(msg, thrown);
        }
    }

    public Level getLevel() {
        if (logger.isTraceEnabled()) {
            return FINEST;
        } else if (logger.isDebugEnabled()) {
            return FINE;
        } else if (logger.isInfoEnabled()) {
            return INFO;
        } else if (logger.isWarnEnabled()) {
            return WARNING;
        } else if (logger.isErrorEnabled()) {
            return SEVERE;
        }
        return OFF;
    }

    public void setLevel(Level newLevel) throws SecurityException {
        // noop
    }

    public boolean isLoggable(Level level) {
        if (level == SEVERE) {
            return logger.isErrorEnabled();
        } else if (level == WARNING) {
            return logger.isWarnEnabled();
        } else if (level == INFO) {
            return logger.isInfoEnabled();
        } else if (level == CONFIG) {
            return logger.isInfoEnabled();
        } else if (level == FINE) {
            return logger.isDebugEnabled();
        } else if (level == FINER) {
            return logger.isDebugEnabled();
        } else if (level == FINEST) {
            return logger.isTraceEnabled();
        } else {
            return false;
        }
    }

    public void log(LogRecord record) {
        log(record.getLevel(), record.getMessage());
    }

    public void log(Level level, String msg, Object param1) {
        log(level, msg);
    }

    public void log(Level level, String msg, Object params[]) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object params[]) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        log(level, msg);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        log(level, msg);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object params[]) {
        log(level, msg);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, ResourceBundle bundle, String msg, Object... params) {
        log(level, msg);
    }

    public void logrb(Level level, ResourceBundle bundle, String msg, Object... params) {
        log(level, msg);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, ResourceBundle bundle, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void logrb(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void entering(String sourceClass, String sourceMethod) {
        // noop
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
        // noop
    }

    public void entering(String sourceClass, String sourceMethod, Object params[]) {
        // noop
    }

    public void exiting(String sourceClass, String sourceMethod) {
        // noop
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
        // noop
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        // noop
    }

    public ResourceBundle getResourceBundle() {
        return null;
    }

    public void setResourceBundle(ResourceBundle bundle) {
        // noop
    }

    public String getResourceBundleName() {
        return null;
    }

    public Filter getFilter() {
        return null;
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        // noop
    }

    public void addHandler(Handler handler) throws SecurityException {
        // noop
    }

    public void removeHandler(Handler handler) throws SecurityException {
        // noop
    }

    public Handler[] getHandlers() {
        return null;
    }

    public boolean getUseParentHandlers() {
        return false;
    }

    public void setUseParentHandlers(boolean useParentHandlers) {
        // noop
    }

    public JulBridgeLogger getParent() {
        return null;
    }

    public void setParent(JulBridgeLogger parent) {
        // noop
    }
}
