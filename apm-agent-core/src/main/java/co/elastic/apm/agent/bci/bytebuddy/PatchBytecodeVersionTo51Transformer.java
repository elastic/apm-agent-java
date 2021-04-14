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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

/**
 * Patches the class file version to 51 (Java 7) in order to support injecting {@code INVOKEDYNAMIC} instructions via
 * {@link Advice.WithCustomMapping#bootstrap} which is important for {@linkplain TracerAwareInstrumentation#indyPlugin() indy plugins}.
 */
public class PatchBytecodeVersionTo51Transformer implements AgentBuilder.Transformer {
    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule) {
        ClassFileVersion classFileVersion = typeDescription.getClassFileVersion();
        if (classFileVersion != null && classFileVersion.getJavaVersion() >= 7) {
            // we can avoid the expensive (and somewhat dangerous) stack frame re-computation if stack frames are already
            // present in the bytecode, which also allows eagerly loading types that might be present in the method
            // body, but not yet loaded by the JVM.
            return builder;
        }
        return builder.visit(new AsmVisitorWrapper.AbstractBase() {
            @Override
            public ClassVisitor wrap(TypeDescription typeDescription, ClassVisitor classVisitor, Implementation.Context context,
                                     TypePool typePool, FieldList<FieldDescription.InDefinedShape> fieldList, MethodList<?> methodList, int writerFlags, int readerFlags) {
                return new ClassVisitor(Opcodes.ASM7, classVisitor) {
                    private boolean patchVersion;

                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        if (version < Opcodes.V1_7) {
                            patchVersion = true;
                            //
                            version = Opcodes.V1_7;
                        } else {
                            throw new IllegalStateException("should not be applied to class file version " + version);
                        }
                        super.visit(version, access, name, signature, superName, interfaces);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        final MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (patchVersion) {
                            return new JSRInlinerAdapter(methodVisitor, access, name, descriptor, signature, exceptions);
                        } else {
                            return methodVisitor;
                        }
                    }
                };
            }

            @Override
            public int mergeWriter(int flags) {
                // class files with version < Java 7 don't require a stack frame map
                // as we're patching the version to at least 7, we have to compute the frames
                return flags | COMPUTE_FRAMES;
            }
        });
    }
}
