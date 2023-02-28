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
package co.elastic.apm.agent.impl.error;

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

/**
 * Additional information added when logging the error.
 */
public class Log implements Recyclable {

    private static final String DEFAULT_LOGGER_NAME = "default";
    private static final String DEFAULT_LEVEL = "error";

    /**
     * The severity of the record.
     */
    private String level = DEFAULT_LEVEL;
    /**
     * The name of the logger instance used.
     */
    private String loggerName = DEFAULT_LOGGER_NAME;
    /**
     * The additionally logged error message.
     * (Required)
     */
    @Nullable
    private String message;
    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    @Nullable
    private String paramMessage;

    /**
     * The severity of the record.
     */
    public String getLevel() {
        return level;
    }

    /**
     * The severity of the record.
     */
    public Log withLevel(String level) {
        this.level = level;
        return this;
    }

    /**
     * The name of the logger instance used.
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * The name of the logger instance used.
     */
    public Log withLoggerName(String loggerName) {
        this.loggerName = loggerName;
        return this;
    }

    /**
     * The additionally logged error message.
     * (Required)
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * The additionally logged error message.
     * (Required)
     */
    public Log withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    @Nullable
    public String getParamMessage() {
        return paramMessage;
    }

    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    public Log withParamMessage(String paramMessage) {
        this.paramMessage = paramMessage;
        return this;
    }

    @Override
    public void resetState() {
        loggerName = DEFAULT_LOGGER_NAME;
        level = DEFAULT_LEVEL;
        message = null;
        paramMessage = null;
    }
}
