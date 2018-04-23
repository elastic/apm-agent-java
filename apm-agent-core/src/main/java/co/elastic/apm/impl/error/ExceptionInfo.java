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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Information about the originally thrown error.
 */
public class ExceptionInfo implements Recyclable {

    private final List<Stacktrace> stacktrace = new ArrayList<>();
    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @Nullable
    private String code;
    /**
     * The original error message.
     * (Required)
     */
    @Nullable
    private String message;
    /**
     * Describes the exception type's module namespace.
     */
    @Nullable
    private String type;

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    @Nullable
    public String getCode() {
        return code;
    }

    /**
     * The error code set when the error happened, e.g. database error code.
     */
    public ExceptionInfo withCode(String code) {
        this.code = code;
        return this;
    }

    /**
     * The original error message.
     * (Required)
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * The original error message.
     * (Required)
     */
    public ExceptionInfo withMessage(@Nullable String message) {
        this.message = message;
        return this;
    }

    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public ExceptionInfo withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    @Override
    public void resetState() {
        code = null;
        stacktrace.clear();
        message = null;
        type = null;
    }
}
