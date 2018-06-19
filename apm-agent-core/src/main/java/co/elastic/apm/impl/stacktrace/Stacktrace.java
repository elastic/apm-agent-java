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

package co.elastic.apm.impl.stacktrace;

import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;


/**
 * Stacktrace
 * <p>
 * A stacktrace frame, contains various bits (most optional) describing the context of the frame
 */
public class Stacktrace implements Recyclable {

    /**
     * The absolute path of the file involved in the stack frame
     */
    @Nullable
    private String absPath;
    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @Nullable
    private String filename;
    /**
     * The function involved in the stack frame
     */
    @Nullable
    private String function;
    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    private boolean libraryFrame;
    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    private long lineno;
    /**
     * The module to which frame belongs to
     */
    @Nullable
    private String module;

    /**
     * The absolute path of the file involved in the stack frame
     */
    @Nullable
    public String getAbsPath() {
        return absPath;
    }

    /**
     * The absolute path of the file involved in the stack frame
     */
    public Stacktrace withAbsPath(@Nullable String absPath) {
        this.absPath = absPath;
        return this;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @Nullable
    public String getFilename() {
        return filename;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    public Stacktrace withFilename(@Nullable String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * The function involved in the stack frame
     */
    @Nullable
    public String getFunction() {
        return function;
    }

    /**
     * The function involved in the stack frame
     */
    public Stacktrace withFunction(@Nullable String function) {
        this.function = function;
        return this;
    }

    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    public boolean isLibraryFrame() {
        return libraryFrame;
    }

    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    public Stacktrace withLibraryFrame(boolean libraryFrame) {
        this.libraryFrame = libraryFrame;
        return this;
    }

    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    public long getLineno() {
        return lineno;
    }

    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    public Stacktrace withLineno(long lineno) {
        this.lineno = lineno;
        return this;
    }

    /**
     * The module to which frame belongs to
     */
    @Nullable
    public String getModule() {
        return module;
    }

    /**
     * The module to which frame belongs to
     */
    public Stacktrace withModule(@Nullable String module) {
        this.module = module;
        return this;
    }

    @Override
    public void resetState() {
        absPath = null;
        filename = null;
        function = null;
        libraryFrame = false;
        lineno = 0;
        module = null;
    }
}
