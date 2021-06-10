/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;
import java.util.Objects;

public class StackFrame {
    @Nullable
    private final String className;
    private final String methodName;

    public static StackFrame of(@Nullable String className, String methodName) {
        return new StackFrame(className, methodName);
    }

    public StackFrame(@Nullable String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    @Nullable
    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void appendSimpleClassName(StringBuilder sb) {
        if (className != null) {
            sb.append(className, className.lastIndexOf('.') + 1, className.length());
        }
    }

    public void appendFileName(StringBuilder replaceBuilder) {
        if (className != null) {
            int fileNameEnd = className.indexOf('$');
            if (fileNameEnd < 0) {
                fileNameEnd = className.length();
            }
            int classNameStart = className.lastIndexOf('.');
            if (classNameStart < fileNameEnd && fileNameEnd <= className.length()) {
                replaceBuilder.append(className, classNameStart + 1, fileNameEnd);
                replaceBuilder.append(".java");
            } else {
                replaceBuilder.append("<Unknown>");
            }
        } else {
            replaceBuilder.append("<Unknown>");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StackFrame that = (StackFrame) o;

        if (!Objects.equals(className, that.className)) return false;
        return methodName.equals(that.methodName);
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + methodName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        if (className == null) {
            return methodName;
        }
        return className + '.' + methodName;
    }
}
