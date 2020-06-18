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
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.dynamic.scaffold.FieldLocator;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.collection.ArrayAccess;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.annotationType;

public class AssignToPostProcessorFactory implements Advice.PostProcessor.Factory {
    private static FieldLocator getFieldLocator(TypeDescription instrumentedType, AssignTo.Field assignTo) {
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

    @Override
    public Advice.PostProcessor make(final MethodDescription.InDefinedShape adviceMethod, final boolean exit) {
        final AnnotationList annotations = adviceMethod.getDeclaredAnnotations()
            .filter(annotationType(AssignTo.Argument.class)
                .or(annotationType(AssignTo.Field.class))
                .or(annotationType(AssignTo.Return.class))
                .or(annotationType(AssignTo.class)));
        if (annotations.isEmpty()) {
            return Advice.PostProcessor.NoOp.INSTANCE;
        }
        final List<Advice.PostProcessor> postProcessors = new ArrayList<>();
        for (AnnotationDescription annotation : annotations) {
            if (annotation.getAnnotationType().represents(AssignTo.Argument.class)) {
                final AssignTo.Argument assignToArgument = annotations.getOnly().prepare(AssignTo.Argument.class).load();
                postProcessors.add(createAssignToArgumentPostProcessor(adviceMethod, exit, assignToArgument));
            } else if (annotation.getAnnotationType().represents(AssignTo.Field.class)) {
                final AssignTo.Field assignToField = annotations.getOnly().prepare(AssignTo.Field.class).load();
                postProcessors.add(createAssignToFieldPostProcessor(adviceMethod, exit, assignToField));
            } else if (annotation.getAnnotationType().represents(AssignTo.Return.class)) {
                final AssignTo.Return assignToReturn = annotations.getOnly().prepare(AssignTo.Return.class).load();
                postProcessors.add(createAssignToReturnPostProcessor(adviceMethod, exit, assignToReturn));
            } else if (annotation.getAnnotationType().represents(AssignTo.class)) {
                final AssignTo assignTo = annotations.getOnly().prepare(AssignTo.class).load();
                for (AssignTo.Argument assignToArgument : assignTo.arguments()) {
                    postProcessors.add(createAssignToArgumentPostProcessor(adviceMethod, exit, assignToArgument));
                }
                for (AssignTo.Field assignToField : assignTo.fields()) {
                    postProcessors.add(createAssignToFieldPostProcessor(adviceMethod, exit, assignToField));
                }
                for (AssignTo.Return assignToReturn : assignTo.returns()) {
                    postProcessors.add(createAssignToReturnPostProcessor(adviceMethod, exit, assignToReturn));
                }
            }
        }
        return new Advice.PostProcessor() {
            @Override
            public StackManipulation resolve(TypeDescription typeDescription, MethodDescription methodDescription, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                final Label jumpToIfNull = new Label();
                return new StackManipulation.Compound(
                    new AddNullCheck(jumpToIfNull, adviceMethod.getReturnType(), MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter())),
                    new AssignToPostProcessorFactory.Compound(postProcessors).resolve(typeDescription, methodDescription, assigner, argumentHandler),
                    new AddLabel(jumpToIfNull)
                );
            }
        };
    }

    private Advice.PostProcessor createAssignToReturnPostProcessor(final MethodDescription.InDefinedShape adviceMethod, final boolean exit, final AssignTo.Return assignToReturn) {
        return new Advice.PostProcessor() {
            @Override
            public StackManipulation resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                final TypeDescription.Generic returnType = adviceMethod.getReturnType();
                if (assignToReturn.index() != -1) {
                    if (!returnType.represents(Object[].class)) {
                        throw new IllegalStateException("Advice method has to return Object[] when setting an index");
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.REFERENCE.loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        IntegerConstant.forValue(assignToReturn.index()),
                        ArrayAccess.REFERENCE.load(),
                        assigner.assign(TypeDescription.Generic.OBJECT, instrumentedMethod.getReturnType(), Assigner.Typing.DYNAMIC),
                        MethodVariableAccess.of(instrumentedMethod.getReturnType()).storeAt(argumentHandler.returned())
                    );
                } else {
                    final StackManipulation assign = assigner.assign(adviceMethod.getReturnType(), instrumentedMethod.getReturnType(), assignToReturn.typing());
                    if (!assign.isValid()) {
                        throw new IllegalStateException("Cannot assign " + adviceMethod.getReturnType() + " to " + instrumentedMethod.getReturnType());
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        assign,
                        MethodVariableAccess.of(instrumentedMethod.getReturnType()).storeAt(argumentHandler.returned())
                    );
                }
            }
        };
    }

    private Advice.PostProcessor createAssignToFieldPostProcessor(final MethodDescription.InDefinedShape adviceMethod, final boolean exit, final AssignTo.Field assignToField) {
        return new Advice.PostProcessor() {
            @Override
            public StackManipulation resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                final FieldDescription field = getFieldLocator(instrumentedType, assignToField).locate(assignToField.value()).getField();

                if (!field.isStatic() && instrumentedMethod.isStatic()) {
                    throw new IllegalStateException("Cannot read non-static field " + field + " from static method " + instrumentedMethod);
                } else if (instrumentedMethod.isConstructor() && !exit) {
                    throw new IllegalStateException("Cannot access non-static field before calling constructor: " + instrumentedMethod);
                }

                final TypeDescription.Generic returnType = adviceMethod.getReturnType();
                if (assignToField.index() != -1) {
                    if (!returnType.represents(Object[].class)) {
                        throw new IllegalStateException("Advice method has to return Object[] when setting an index");
                    }
                    final Label jumpToIfNull = new Label();
                    final StackManipulation load = MethodVariableAccess.REFERENCE.loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter());
                    return new StackManipulation.Compound(
                        new AddNullCheck(jumpToIfNull, TypeDescription.Generic.OBJECT, load),
                        MethodVariableAccess.loadThis(),
                        load,
                        loadArrayElement(assignToField.index()),
                        assigner.assign(TypeDescription.Generic.OBJECT, field.getType(), Assigner.Typing.DYNAMIC),
                        FieldAccess.forField(field).write(),
                        new AddLabel(jumpToIfNull)
                    );
                } else {
                    final StackManipulation assign = assigner.assign(adviceMethod.getReturnType(), field.getType(), assignToField.typing());
                    if (!assign.isValid()) {
                        throw new IllegalStateException("Cannot assign " + adviceMethod.getReturnType() + " to " + field.getType());
                    }
                    final Label jumpToIfNull = new Label();
                    final StackManipulation load = MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter());
                    return new StackManipulation.Compound(
                        new AddNullCheck(jumpToIfNull, TypeDescription.Generic.OBJECT, load),
                        MethodVariableAccess.loadThis(),
                        load,
                        assign,
                        FieldAccess.forField(field).write(),
                        new AddLabel(jumpToIfNull)
                    );
                }
            }
        };
    }

    private Advice.PostProcessor createAssignToArgumentPostProcessor(final MethodDescription.InDefinedShape adviceMethod, final boolean exit, final AssignTo.Argument assignToArgument) {
        return new Advice.PostProcessor() {
            @Override
            public StackManipulation resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                final ParameterDescription param = instrumentedMethod.getParameters().get(assignToArgument.value());
                final TypeDescription.Generic returnType = adviceMethod.getReturnType();
                if (assignToArgument.index() != -1) {
                    if (!returnType.represents(Object[].class)) {
                        throw new IllegalStateException("Advice method has to return Object[] when setting an index");
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.REFERENCE.loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        IntegerConstant.forValue(assignToArgument.index()),
                        ArrayAccess.REFERENCE.load(),
                        assigner.assign(TypeDescription.Generic.OBJECT, param.getType(), Assigner.Typing.DYNAMIC),
                        MethodVariableAccess.store(param)
                    );
                } else {
                    final StackManipulation assign = assigner.assign(returnType, param.getType(), assignToArgument.typing());
                    if (!assign.isValid()) {
                        throw new IllegalStateException("Cannot assign " + adviceMethod.getReturnType() + " to " + param.getType());
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        assign,
                        MethodVariableAccess.store(param)
                    );
                }
            }
        };
    }

    private StackManipulation loadArrayElement(int i) {
        return new StackManipulation.Compound(
            IntegerConstant.forValue(i),
            ArrayAccess.REFERENCE.load());
    }

    public static class Compound implements Advice.PostProcessor {

        /**
         * The represented post processors.
         */
        private final List<Advice.PostProcessor> postProcessors;

        /**
         * Creates a new compound post processor.
         *
         * @param postProcessors The represented post processors.
         */
        public Compound(List<Advice.PostProcessor> postProcessors) {
            this.postProcessors = postProcessors;
        }

        /**
         * {@inheritDoc}
         */
        public StackManipulation resolve(TypeDescription instrumentedType,
                                         MethodDescription instrumentedMethod,
                                         Assigner assigner,
                                         Advice.ArgumentHandler argumentHandler) {
            List<StackManipulation> stackManipulations = new ArrayList<StackManipulation>(postProcessors.size());
            for (Advice.PostProcessor postProcessor : postProcessors) {
                stackManipulations.add(postProcessor.resolve(instrumentedType, instrumentedMethod, assigner, argumentHandler));
            }
            return new StackManipulation.Compound(stackManipulations);
        }
    }

    private static class AddNullCheck implements StackManipulation {
        private final Label jumpToIfNull;
        private final TypeDescription.Generic type;
        private final StackManipulation load;

        public AddNullCheck(Label jumpToIfNull, TypeDescription.Generic type, StackManipulation load) {
            this.jumpToIfNull = jumpToIfNull;
            this.type = type;
            this.load = load;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public Size apply(MethodVisitor methodVisitor, Implementation.Context context) {
            Size size = new Size(0, 0);
            if (!type.isPrimitive()) {
                size = size.aggregate(load.apply(methodVisitor, context));
                methodVisitor.visitJumpInsn(Opcodes.IFNULL, jumpToIfNull);
            }
            return size;
        }
    }

    private static class AddLabel implements StackManipulation {
        private final Label label;

        public AddLabel(Label label) {
            this.label = label;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public Size apply(MethodVisitor methodVisitor, Implementation.Context context) {
            methodVisitor.visitLabel(label);
            return new Size(0, 0);
        }
    }
}
