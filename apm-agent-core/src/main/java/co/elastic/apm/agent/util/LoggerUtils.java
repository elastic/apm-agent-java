package co.elastic.apm.agent.util;

import org.slf4j.Logger;
import org.slf4j.Marker;

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

        @Override
        public boolean isTraceEnabled() {
            return delegate.isTraceEnabled();
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
        public boolean isTraceEnabled(Marker marker) {
            return delegate.isTraceEnabled(marker);
        }

        @Override
        public void trace(Marker marker, String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(marker, msg);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(marker, format, arg);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(marker, format, arg1, arg2);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object... argArray) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(marker, format, argArray);
            }
        }

        @Override
        public void trace(Marker marker, String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.trace(marker, msg, t);
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return delegate.isDebugEnabled();
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
        public boolean isDebugEnabled(Marker marker) {
            return delegate.isDebugEnabled(marker);
        }

        @Override
        public void debug(Marker marker, String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(marker, msg);
            }
        }

        @Override
        public void debug(Marker marker, String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(marker, format, arg);
            }
        }

        @Override
        public void debug(Marker marker, String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(marker, format, arg1, arg2);
            }
        }

        @Override
        public void debug(Marker marker, String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(marker, format, arguments);
            }
        }

        @Override
        public void debug(Marker marker, String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.debug(marker, msg, t);
            }
        }

        @Override
        public boolean isInfoEnabled() {
            return delegate.isInfoEnabled();
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
        public boolean isInfoEnabled(Marker marker) {
            return delegate.isInfoEnabled(marker);
        }

        @Override
        public void info(Marker marker, String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(marker, msg);
            }
        }

        @Override
        public void info(Marker marker, String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(marker, format, arg);
            }
        }

        @Override
        public void info(Marker marker, String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(marker, format, arg1, arg2);
            }
        }

        @Override
        public void info(Marker marker, String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(marker, format, arguments);
            }
        }

        @Override
        public void info(Marker marker, String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.info(marker, msg, t);
            }
        }

        @Override
        public boolean isWarnEnabled() {
            return delegate.isWarnEnabled();
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
        public boolean isWarnEnabled(Marker marker) {
            return delegate.isWarnEnabled(marker);
        }

        @Override
        public void warn(Marker marker, String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(marker, msg);
            }
        }

        @Override
        public void warn(Marker marker, String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(marker, format, arg);
            }
        }

        @Override
        public void warn(Marker marker, String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(marker, format, arg1, arg2);
            }
        }

        @Override
        public void warn(Marker marker, String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(marker, format, arguments);
            }
        }

        @Override
        public void warn(Marker marker, String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.warn(marker, msg, t);
            }
        }

        @Override
        public boolean isErrorEnabled() {
            return delegate.isErrorEnabled();
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

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return delegate.isErrorEnabled(marker);
        }

        @Override
        public void error(Marker marker, String msg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(marker, msg);
            }
        }

        @Override
        public void error(Marker marker, String format, Object arg) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(marker, format, arg);
            }
        }

        @Override
        public void error(Marker marker, String format, Object arg1, Object arg2) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(marker, format, arg1, arg2);
            }
        }

        @Override
        public void error(Marker marker, String format, Object... arguments) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(marker, format, arguments);
            }
        }

        @Override
        public void error(Marker marker, String msg, Throwable t) {
            if (alreadyLogged.compareAndSet(false, true)) {
                delegate.error(marker, msg, t);
            }
        }
    }
}
