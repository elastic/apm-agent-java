/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.OpenedClassReader;
import org.objectweb.asm.ClassVisitor;

public enum MinimumClassFileVersionValidator implements AsmVisitorWrapper {

    V1_4(ClassFileVersion.JAVA_V4, new UnsupportedClassFileVersionException("4")),
    V1_5(ClassFileVersion.JAVA_V5, new UnsupportedClassFileVersionException("5"));

    private final ClassFileVersion minimumClassFileVersion;
    private final UnsupportedClassFileVersionException exception;

    MinimumClassFileVersionValidator(ClassFileVersion minimumClassFileVersion, UnsupportedClassFileVersionException exception) {
        this.minimumClassFileVersion = minimumClassFileVersion;
        this.exception = exception;
    }

    @Override
    public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext,
                             TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
        return new ClassVisitor(OpenedClassReader.ASM_API, classVisitor) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                final ClassFileVersion classFileVersion = ClassFileVersion.ofMinorMajor(version);
                if (!classFileVersion.isAtLeast(minimumClassFileVersion)) {
                    throw exception;
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }
        };
    }

    @Override
    public int mergeWriter(int flags) {
        return flags;
    }

    @Override
    public int mergeReader(int flags) {
        return flags;
    }

    public static class UnsupportedClassFileVersionException extends RuntimeException {

        private final String minVersion;

        private UnsupportedClassFileVersionException(String minVersion) {
            this.minVersion = minVersion;
        }

        public String getMinVersion() {
            return minVersion;
        }

        /*
         * avoids the expensive creation of the stack trace which is not needed
         */
        @Override
        public synchronized Throwable fillInStackTrace()
        {
            return this;
        }
    }
}
