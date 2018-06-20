/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * This class then forwards the logger calls to a slf4j {@link org.slf4j.Logger}.
 * </p>
 */
public class JulBridgeLogger {

    public static final String GLOBAL_LOGGER_NAME = "global";
    public static final JulBridgeLogger global = new JulBridgeLogger(LoggerFactory.getLogger(GLOBAL_LOGGER_NAME));
    private final Logger logger;

    private JulBridgeLogger(Logger logger) {
        this.logger = logger;
    }

    public static final JulBridgeLogger getGlobal() {
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
        log(level, msg, (Object) null);
    }

    public void log(LogRecord record) {
        log(record.getLevel(), record.getMessage(), record.getParameters());
    }

    public void log(Level level, String msg, Object param1) {
        if (level == SEVERE) {
            logger.error(msg, param1);
        } else if (level == WARNING) {
            logger.warn(msg, param1);
        } else if (level == INFO) {
            logger.info(msg, param1);
        } else if (level == CONFIG) {
            logger.info(msg, param1);
        } else if (level == FINE) {
            logger.debug(msg, param1);
        } else if (level == FINER) {
            logger.debug(msg, param1);
        } else if (level == FINEST) {
            logger.trace(msg, param1);
        }
    }

    public void log(Level level, String msg, Object params[]) {
        log(level, msg, (Object) params);
    }

    public void log(Level level, String msg, Throwable thrown) {
        log(level, msg, (Object) thrown);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        log(level, msg, param1);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object params[]) {
        log(level, msg, params);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        log(level, msg);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        log(level, msg, param1);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object params[]) {
        log(level, msg, params);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, ResourceBundle bundle, String msg, Object... params) {
        log(level, msg, params);
    }

    public void logrb(Level level, ResourceBundle bundle, String msg, Object... params) {
        log(level, msg, params);
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
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
    }

    public void entering(String sourceClass, String sourceMethod, Object params[]) {
    }

    public void exiting(String sourceClass, String sourceMethod) {
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
    }

    public ResourceBundle getResourceBundle() {
        return null;
    }

    public void setResourceBundle(ResourceBundle bundle) {
    }

    public String getResourceBundleName() {
        return null;
    }

    public Filter getFilter() {
        return null;
    }

    public void setFilter(Filter newFilter) throws SecurityException {
    }

    public Level getLevel() {
        return null;
    }

    public void setLevel(Level newLevel) throws SecurityException {
    }

    public boolean isLoggable(Level level) {
        return false;
    }

    public void addHandler(Handler handler) throws SecurityException {
    }

    public void removeHandler(Handler handler) throws SecurityException {
    }

    public Handler[] getHandlers() {
        return null;
    }

    public boolean getUseParentHandlers() {
        return false;
    }

    public void setUseParentHandlers(boolean useParentHandlers) {
    }

    public JulBridgeLogger getParent() {
        return null;
    }

    public void setParent(JulBridgeLogger parent) {
    }
}

