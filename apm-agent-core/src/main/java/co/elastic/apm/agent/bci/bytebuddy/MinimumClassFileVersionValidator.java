/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.OpenedClassReader;

public enum MinimumClassFileVersionValidator implements AsmVisitorWrapper {

    INSTANCE;

    private static final ClassFileVersion MINIMUM_CLASS_FILE_VERSION = ClassFileVersion.JAVA_V5;

    @Override
    public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext,
                             TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
        return new ClassVisitor(OpenedClassReader.ASM_API, classVisitor) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                final ClassFileVersion classFileVersion = ClassFileVersion.ofMinorMajor(version);
                if (!classFileVersion.isAtLeast(MINIMUM_CLASS_FILE_VERSION)) {
                    throw UnsupportedClassFileVersionException.INSTANCE;
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
        static final UnsupportedClassFileVersionException INSTANCE = new UnsupportedClassFileVersionException();

        private UnsupportedClassFileVersionException() {
            // singleton
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
