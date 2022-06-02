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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class LoggerUtils {

    public static Logger logOnce(Logger logger) {
        return new LogOnceLogger(logger);
    }

    private static class LogOnceLogger implements Logger {
        private final Logger delegate;
        private final AtomicBoolean alreadyLogged = new AtomicBoolean(false);

        private LogOnceLogger(Logger delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        private boolean isEnabled(boolean delegateEnabled) {
            if (alreadyLogged.get()) {
                return false;
            }
            return delegateEnabled;
        }

        @Override
        public boolean isTraceEnabled() {
            return isEnabled(delegate.isTraceEnabled());
        }

        @Override
        public void trace(String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(msg);
            }
        }

        @Override
        public void trace(String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(format, arg);
            }
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(format, arg1, arg2);
            }
        }

        @Override
        public void trace(String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(format, arguments);
            }
        }

        @Override
        public void trace(String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(msg, t);
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return isEnabled(delegate.isDebugEnabled());
        }

        @Override
        public void debug(String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(msg);
            }
        }

        @Override
        public void debug(String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(format, arg);
            }
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(format, arg1, arg2);
            }
        }

        @Override
        public void debug(String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(format, arguments);
            }
        }

        @Override
        public void debug(String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(msg, t);
            }
        }

        @Override
        public boolean isInfoEnabled() {
            return isEnabled(delegate.isInfoEnabled());
        }

        @Override
        public void info(String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(msg);
            }
        }

        @Override
        public void info(String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(format, arg);
            }
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(format, arg1, arg2);
            }
        }

        @Override
        public void info(String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(format, arguments);
            }
        }

        @Override
        public void info(String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(msg, t);
            }
        }

        @Override
        public boolean isWarnEnabled() {
            return isEnabled(delegate.isWarnEnabled());
        }

        @Override
        public void warn(String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(msg);
            }
        }

        @Override
        public void warn(String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(format, arg);
            }
        }

        @Override
        public void warn(String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(format, arguments);
            }
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(format, arg1, arg2);
            }
        }

        @Override
        public void warn(String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(msg, t);
            }
        }

        @Override
        public boolean isErrorEnabled() {
            return isEnabled(delegate.isErrorEnabled());
        }

        @Override
        public void error(String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(msg);
            }
        }

        @Override
        public void error(String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(format, arg);
            }
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(format, arg1, arg2);
            }
        }

        @Override
        public void error(String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(format, arguments);
            }
        }

        @Override
        public void error(String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(msg, t);
            }
        }
    }
}
