/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Additional information added when logging the error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Log implements Recyclable {

    private static final String DEFAULT_LOGGER_NAME = "default";
    private static final String DEFAULT_LEVEL = "error";

    @JsonProperty("stacktrace")
    private final List<Stacktrace> stacktrace = new ArrayList<>();
    /**
     * The severity of the record.
     */
    @JsonProperty("level")
    private String level = DEFAULT_LEVEL;
    /**
     * The name of the logger instance used.
     */
    @JsonProperty("logger_name")
    private String loggerName = DEFAULT_LOGGER_NAME;
    /**
     * The additionally logged error message.
     * (Required)
     */
    @Nullable
    @JsonProperty("message")
    private String message;
    /**
     * A parametrized message. E.g. 'Could not connect to %s'. The property message is still required, and should be equal to the param_message, but with placeholders replaced. In some situations the param_message is used to group errors together. The string is not interpreted, so feel free to use whichever placeholders makes sense in the client languange.
     */
    @Nullable
    @JsonProperty("param_message")
    private String paramMessage;

    /**
     * The severity of the record.
     */
    @JsonProperty("level")
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
    @JsonProperty("logger_name")
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
    @JsonProperty("message")
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
    @JsonProperty("param_message")
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

    @JsonProperty("stacktrace")
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @Override
    public void resetState() {
        loggerName = DEFAULT_LOGGER_NAME;
        level = DEFAULT_LEVEL;
        message = null;
        paramMessage = null;
        stacktrace.clear();
    }
}
