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
package co.elastic.apm.agent.bci.bytebuddy.postprocessor;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.dynamic.scaffold.FieldLocator;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;

import static net.bytebuddy.matcher.ElementMatchers.annotationType;

public class  AssignToFieldPostProcessorFactory implements Advice.PostProcessor.Factory {
    @Override
    public Advice.PostProcessor make(final MethodDescription.InDefinedShape adviceMethod, boolean exit) {
        final AnnotationList annotations = adviceMethod.getDeclaredAnnotations().filter(annotationType(AssignToField.class));
        if (!annotations.isEmpty()) {
            final AssignToField assignTo = annotations.getOnly().prepare(AssignToField.class).load();
            return new Advice.PostProcessor() {
                @Override
                public StackManipulation resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                    final FieldDescription field = getFieldLocator(instrumentedType, assignTo).locate(assignTo.value()).getField();

                    if (!field.isStatic() && instrumentedMethod.isStatic()) {
                        throw new IllegalStateException("Cannot read non-static field " + field + " from static method " + instrumentedMethod);
                    } else if (instrumentedMethod.isConstructor() && !exit) {
                        throw new IllegalStateException("Cannot access non-static field before calling constructor: " + instrumentedMethod);
                    }

                    final StackManipulation assign = assigner.assign(adviceMethod.getReturnType(), field.getType(), assignTo.typing());
                    if (!assign.isValid()) {
                        throw new IllegalStateException("Cannot assign " + adviceMethod.getReturnType() + " to " + field.getType());
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.loadThis(),
                        MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        assign,
                        FieldAccess.forField(field).write()
                    );
                }

            };
        } else {
            return Advice.PostProcessor.NoOp.INSTANCE;
        }
    }

    private static FieldLocator getFieldLocator(TypeDescription instrumentedType, AssignToField assignTo) {
        if (assignTo.declaringType() == Void.class) {
            return new FieldLocator.ForClassHierarchy(instrumentedType);
        } else {
            final TypeDescription declaringType = TypeDescription.ForLoadedType.of(assignTo.declaringType());
            if (!declaringType.represents(TargetType.class) && !instrumentedType.isAssignableTo(declaringType)) {
                throw new IllegalStateException(declaringType + " is no super type of " + instrumentedType);
            }
            return new FieldLocator.ForExactType(declaringType);
        }
    }
}
