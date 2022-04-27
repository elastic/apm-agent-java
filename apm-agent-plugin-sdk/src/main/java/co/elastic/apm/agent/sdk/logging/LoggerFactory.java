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
package co.elastic.apm.agent.sdk.logging;

import javax.annotation.Nullable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;

public class LoggerFactory {

    private static volatile ILoggerFactory iLoggerFactory;

    public static void initialize(ILoggerFactory iLoggerFactory) {
        LoggerFactory.iLoggerFactory = iLoggerFactory;
    }

    /**
     * Return a logger named according to the name parameter.
     * <br/>
     * A lazy-initialization logger implementation will be returned when this method is called before {@link #initialize(ILoggerFactory)}
     *
     * @param name The name of the logger.
     * @return logger
     */
    public static Logger getLogger(String name) {
        Logger logger = getLoggerFromFactory(name);
        return logger != null ? logger : new LazyInitLogger(name);
    }

    /**
     * Return a logger named corresponding to the class passed as parameter.
     *
     * @param clazz the returned logger will be named after clazz
     * @return logger
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    @Nullable
    private static Logger getLoggerFromFactory(final String name) {
        if (iLoggerFactory == null) {
            return null;
        }

        return AccessController.doPrivileged(new PrivilegedAction<Logger>() {
            @Override
            public Logger run() {
                return iLoggerFactory.getLogger(name);
            }
        });
    }

    /**
     * Lazy-initialization logger when the {@link #initialize(ILoggerFactory)} method hasn't been called yet. Allows to
     * make calls to {@link #getLogger} before {@link #initialize} to have a valid logger.
     */
    private static class LazyInitLogger implements Logger {

        private final String name;
        private volatile Logger delegate = null;

        LazyInitLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        private Logger getDelegate() {
            if (delegate != null) {
                return delegate;
            }

            delegate = getLoggerFromFactory(name);
            return delegate != null ? delegate : NoopLogger.INSTANCE;
        }

        @Override
        public boolean isTraceEnabled() {
            return getDelegate().isTraceEnabled();
        }

        @Override
        public void trace(String msg) {
            getDelegate().trace(msg);
        }

        @Override
        public void trace(String format, Object arg) {
            getDelegate().trace(format, arg);
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            getDelegate().trace(format, arg1, arg2);
        }

        @Override
        public void trace(String format, Object... arguments) {
            getDelegate().trace(format, arguments);
        }

        @Override
        public void trace(String msg, Throwable t) {
            getDelegate().trace(msg, t);
        }

        @Override
        public boolean isDebugEnabled() {
            return getDelegate().isDebugEnabled();
        }

        @Override
        public void debug(String msg) {
            getDelegate().debug(msg);
        }

        @Override
        public void debug(String format, Object arg) {
            getDelegate().debug(format, arg);
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            getDelegate().debug(format, arg1, arg2);
        }

        @Override
        public void debug(String format, Object... arguments) {
            getDelegate().debug(format, arguments);
        }

        @Override
        public void debug(String msg, Throwable t) {
            getDelegate().debug(msg, t);
        }

        @Override
        public boolean isInfoEnabled() {
            return getDelegate().isInfoEnabled();
        }

        @Override
        public void info(String msg) {
            getDelegate().info(msg);
        }

        @Override
        public void info(String format, Object arg) {
            getDelegate().info(format, arg);
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            getDelegate().info(format, arg1, arg2);
        }

        @Override
        public void info(String format, Object... arguments) {
            getDelegate().info(format, arguments);
        }

        @Override
        public void info(String msg, Throwable t) {
            getDelegate().info(msg, t);
        }

        @Override
        public boolean isWarnEnabled() {
            return getDelegate().isWarnEnabled();
        }

        @Override
        public void warn(String msg) {
            getDelegate().warn(msg);
        }

        @Override
        public void warn(String format, Object arg) {
            getDelegate().warn(format, arg);
        }

        @Override
        public void warn(String format, Object... arguments) {
            getDelegate().warn(format, arguments);
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            getDelegate().warn(format, arg1, arg2);
        }

        @Override
        public void warn(String msg, Throwable t) {
            getDelegate().warn(msg, t);
        }

        @Override
        public boolean isErrorEnabled() {
            return getDelegate().isErrorEnabled();
        }

        @Override
        public void error(String msg) {
            getDelegate().error(msg);
        }

        @Override
        public void error(String format, Object arg) {
            getDelegate().error(format, arg);
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            getDelegate().error(format, arg1, arg2);
        }

        @Override
        public void error(String format, Object... arguments) {
            getDelegate().error(format, arguments);
        }

        @Override
        public void error(String msg, Throwable t) {
            getDelegate().error(msg, t);
        }
    }

    private static class NoopLogger implements Logger {

        static final NoopLogger INSTANCE = new NoopLogger();

        private NoopLogger() {
        }

        @Override
        public String getName() {
            return "null";
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String msg) {

        }

        @Override
        public void trace(String format, Object arg) {

        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {

        }

        @Override
        public void trace(String format, Object... arguments) {

        }

        @Override
        public void trace(String msg, Throwable t) {

        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String msg) {

        }

        @Override
        public void debug(String format, Object arg) {

        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {

        }

        @Override
        public void debug(String format, Object... arguments) {

        }

        @Override
        public void debug(String msg, Throwable t) {

        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(String msg) {

        }

        @Override
        public void info(String format, Object arg) {

        }

        @Override
        public void info(String format, Object arg1, Object arg2) {

        }

        @Override
        public void info(String format, Object... arguments) {

        }

        @Override
        public void info(String msg, Throwable t) {

        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(String msg) {

        }

        @Override
        public void warn(String format, Object arg) {

        }

        @Override
        public void warn(String format, Object... arguments) {

        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {

        }

        @Override
        public void warn(String msg, Throwable t) {

        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(String msg) {

        }

        @Override
        public void error(String format, Object arg) {

        }

        @Override
        public void error(String format, Object arg1, Object arg2) {

        }

        @Override
        public void error(String format, Object... arguments) {

        }

        @Override
        public void error(String msg, Throwable t) {

        }
    }
}
