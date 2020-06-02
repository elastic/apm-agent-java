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
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;

import static net.bytebuddy.matcher.ElementMatchers.annotationType;

public class AssignToReturnPostProcessorFactory implements Advice.PostProcessor.Factory {
    @Override
    public Advice.PostProcessor make(final MethodDescription.InDefinedShape adviceMethod, final boolean exit) {
        final AnnotationList annotations = adviceMethod.getDeclaredAnnotations().filter(annotationType(AssignToReturn.class));
        if (!annotations.isEmpty()) {
            final AssignToReturn assignTo = annotations.getOnly().prepare(AssignToReturn.class).load();
            return new Advice.PostProcessor() {
                @Override
                public StackManipulation resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler) {
                    final StackManipulation assign = assigner.assign(adviceMethod.getReturnType(), instrumentedMethod.getReturnType(), assignTo.typing());
                    if (!assign.isValid()) {
                        throw new IllegalStateException("Cannot assign " + adviceMethod.getReturnType() + " to " + instrumentedMethod.getReturnType());
                    }
                    return new StackManipulation.Compound(
                        MethodVariableAccess.of(adviceMethod.getReturnType()).loadFrom(exit ? argumentHandler.exit() : argumentHandler.enter()),
                        assign,
                        MethodVariableAccess.of(instrumentedMethod.getReturnType()).storeAt(argumentHandler.returned())
                    );
                }
            };
        } else {
            return Advice.PostProcessor.NoOp.INSTANCE;
        }
    }
}
