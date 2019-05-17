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

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import javax.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AnnotationValueOffsetMappingFactory implements Advice.OffsetMapping.Factory<AnnotationValueOffsetMappingFactory.AnnotationValueExtractor> {

    @Override
    public Class<AnnotationValueExtractor> getAnnotationType() {
        return AnnotationValueExtractor.class;
    }

    @Override
    public Advice.OffsetMapping make(final ParameterDescription.InDefinedShape target,
                                     final AnnotationDescription.Loadable<AnnotationValueExtractor> annotation,
                                     final AdviceType adviceType) {
        return new Advice.OffsetMapping() {
            @Override
            public Target resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler, Sort sort) {
                AnnotationValueExtractor annotationValueExtractor = annotation.loadSilent();
                AnnotationSource annotationSource = (AnnotationType.CLASS.equals(annotationValueExtractor.type()))? instrumentedType: instrumentedMethod;
                Object object = getAnnotationValue(annotationSource, annotationValueExtractor);
                // FIXME should we check here on CoreConfiguration.isUseAnnotationValueForTransactionName() ?
                AnnotationSource superClassAnnotationSource = instrumentedType.getSuperClass().asErasure();
                while (object == null && !"java.lang.Object".equals(((TypeDescription) superClassAnnotationSource).getCanonicalName())) {
                    object = getAnnotationValue(superClassAnnotationSource, annotationValueExtractor);
                    superClassAnnotationSource = ((TypeDescription) superClassAnnotationSource).getSuperClass().asErasure();
                }

                TypeList interfaces = instrumentedType.getInterfaces().asErasures();
                for (int i = 0; object == null && i < interfaces.size(); i++) {
                    TypeDescription typeDescription = interfaces.get(i);
                    object = getAnnotationValue(typeDescription.asErasure(), annotationValueExtractor);
                }
                return Target.ForStackManipulation.of(object);
            }
        };
    }

    @Nullable
    private Object getAnnotationValue(AnnotationSource annotationSource, AnnotationValueExtractor annotationValueExtractor) {
        for (TypeDescription typeDescription : annotationSource.getDeclaredAnnotations().asTypeList()) {
            if (named(annotationValueExtractor.annotationClassName()).matches(typeDescription)) {
                for (MethodDescription.InDefinedShape annotationMethod : typeDescription.getDeclaredMethods()) {
                    if (annotationMethod.getName().equals(annotationValueExtractor.method())) {
                        return annotationSource.getDeclaredAnnotations().ofType(typeDescription).getValue(annotationMethod).resolve();
                    }
                }
            }
        }
        return null;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface AnnotationValueExtractor {
        String annotationClassName();

        String method();

        AnnotationType type();
    }

    public enum AnnotationType {
        METHOD,
        CLASS
    }

}
