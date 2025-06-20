/*
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
 */
package co.elastic.apm.agent.sdk.bytebuddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Enables using {@link SimpleMethodSignature} in {@link net.bytebuddy.asm.Advice.OnMethodEnter} and
 * {@link net.bytebuddy.asm.Advice.OnMethodExit} methods.
 */
public class SimpleMethodSignatureOffsetMappingFactory implements Advice.OffsetMapping.Factory<SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature> {

    @Override
    public Class<SimpleMethodSignature> getAnnotationType() {
        return SimpleMethodSignature.class;
    }

    @Override
    public Advice.OffsetMapping make(ParameterDescription.InDefinedShape target,
                                     AnnotationDescription.Loadable<SimpleMethodSignature> annotation,
                                     AdviceType adviceType) {
        return new Advice.OffsetMapping() {
            @Override
            public Target resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner,
                                  Advice.ArgumentHandler argumentHandler, Sort sort) {
                final String className = instrumentedMethod.getDeclaringType().getTypeName();
                final String simpleClassName = ClassNameParser.parse(className);
                final String signature = String.format("%s#%s", simpleClassName, instrumentedMethod.getName());
                return Target.ForStackManipulation.of(signature);
            }
        };
    }

    /**
     * Indicates that the annotated parameter should be mapped to a string representation of the instrumented method,
     * a constant representing {@link Class#getSimpleName()}{@code #}{@link Method#getName()},
     * for example {@code FooClass#barMethod}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface SimpleMethodSignature {
    }


}
