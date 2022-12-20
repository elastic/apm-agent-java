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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.sdk.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logger implementation that can be muted
 */
class MuteableLogger implements Logger {

    private final Logger delegate;
    private final AtomicBoolean muted = new AtomicBoolean();

    MuteableLogger(Logger delegate) {
        this.delegate = delegate;
    }

    /**
     * Controls the muted status of this logger.
     *
     * @param mute {@literal true} to make this logger silent, {@literal false} to make it behave like a regular logger
     */
    public void setMuted(boolean mute) {
        this.muted.set(mute);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return !muted.get() && delegate.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        if (muted.get()) {
            return;
        }
        delegate.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        if (muted.get()) {
            return;
        }
        delegate.trace(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (muted.get()) {
            return;
        }
        delegate.trace(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (muted.get()) {
            return;
        }
        delegate.trace(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (muted.get()) {
            return;
        }
        delegate.trace(msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return !muted.get() && delegate.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        if (muted.get()) {
            return;
        }
        delegate.debug(msg);
    }

    @Override
    public void debug(String format, Object arg) {
        if (muted.get()) {
            return;
        }
        delegate.debug(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (muted.get()) {
            return;
        }
        delegate.debug(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (muted.get()) {
            return;
        }
        delegate.debug(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (muted.get()) {
            return;
        }
        delegate.debug(msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return !muted.get() && delegate.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        if (muted.get()) {
            return;
        }
        delegate.info(msg);
    }

    @Override
    public void info(String format, Object arg) {
        if (muted.get()) {
            return;
        }
        delegate.info(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (muted.get()) {
            return;
        }
        delegate.info(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        if (muted.get()) {
            return;
        }
        delegate.info(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        if (muted.get()) {
            return;
        }
        delegate.info(msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return !muted.get() && delegate.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        if (muted.get()) {
            return;
        }
        delegate.warn(msg);
    }

    @Override
    public void warn(String format, Object arg) {
        if (muted.get()) {
            return;
        }
        delegate.warn(format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (muted.get()) {
            return;
        }
        delegate.warn(format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (muted.get()) {
            return;
        }
        delegate.warn(format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (muted.get()) {
            return;
        }
        delegate.warn(msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return !muted.get() && delegate.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        if (muted.get()) {
            return;
        }
        delegate.error(msg);
    }

    @Override
    public void error(String format, Object arg) {
        if (muted.get()) {
            return;
        }
        delegate.error(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (muted.get()) {
            return;
        }
        delegate.error(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        if (muted.get()) {
            return;
        }
        delegate.error(format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        if (muted.get()) {
            return;
        }
        delegate.error(msg, t);
    }
}
