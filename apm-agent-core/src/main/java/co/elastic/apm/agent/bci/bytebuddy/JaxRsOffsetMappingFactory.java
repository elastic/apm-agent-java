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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
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

public class JaxRsOffsetMappingFactory implements Advice.OffsetMapping.Factory<JaxRsOffsetMappingFactory.JaxRsPath> {

    private final CoreConfiguration coreConfiguration;

    public JaxRsOffsetMappingFactory(ElasticApmTracer tracer) {
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @Override
    public Class<JaxRsPath> getAnnotationType() {
        return JaxRsPath.class;
    }

    @Override
    public Advice.OffsetMapping make(ParameterDescription.InDefinedShape target, AnnotationDescription.Loadable<JaxRsPath> annotation, AdviceType adviceType) {
        return new Advice.OffsetMapping() {
            @Override
            public Target resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner, Advice.ArgumentHandler argumentHandler, Sort sort) {
                Object value = null;
                if (coreConfiguration.isUseAnnotationValueForTransactionName()) {
                    value = getTransactionAnnotationValueFromAnnotations(instrumentedMethod, instrumentedType);
                }
                return Target.ForStackManipulation.of(value);
            }
        };
    }

    private String getTransactionAnnotationValueFromAnnotations(MethodDescription instrumentedMethod, TypeDescription instrumentedType) {
        TransactionAnnotationValue transactionAnnotationValue = new TransactionAnnotationValue();
        String methodName = instrumentedMethod.getName();
        while (!"java.lang.Object".equals(instrumentedType.getCanonicalName())) {
            getAnnotationValueFromAnnotationSource(transactionAnnotationValue, instrumentedType, true);
            for (MethodDescription.InDefinedShape annotationMethod : instrumentedType.getDeclaredMethods().filter(named(methodName)).asDefined()) {
                getAnnotationValueFromAnnotationSource(transactionAnnotationValue, annotationMethod, false);
            }
            findInInterfaces(transactionAnnotationValue, instrumentedType, methodName);
            instrumentedType = instrumentedType.getSuperClass().asErasure();
        }
        return transactionAnnotationValue.buildTransactionName();
    }

    private void findInInterfaces(TransactionAnnotationValue transactionAnnotationValue, TypeDescription classTypeDescription, String methodName) {
        TypeList interfaces = classTypeDescription.getInterfaces().asErasures();
        for (int i = 0; i < interfaces.size(); i++) {
            TypeDescription interfaceDescription = interfaces.get(i);
            getAnnotationValueFromAnnotationSource(transactionAnnotationValue, interfaceDescription, false);
            for (MethodDescription.InDefinedShape annotationMethod : interfaceDescription.getDeclaredMethods().filter(named(methodName))) {
                getAnnotationValueFromAnnotationSource(transactionAnnotationValue, annotationMethod, false);
            }
            findInInterfaces(transactionAnnotationValue, interfaceDescription, methodName);
        }
    }

    public void getAnnotationValueFromAnnotationSource(TransactionAnnotationValue transactionAnnotationValue, AnnotationSource annotationSource, Boolean isClassLevelPath) {
        for (TypeDescription classMethodTypeDescription : annotationSource.getDeclaredAnnotations().asTypeList()) {
            String canonicalName = classMethodTypeDescription.getCanonicalName();
            switch (canonicalName) {
                case "javax.ws.rs.Path":
                    for (MethodDescription.InDefinedShape annotationMethod : classMethodTypeDescription.getDeclaredMethods().filter(named("value"))) {
                        Object pathValue = annotationSource.getDeclaredAnnotations().ofType(classMethodTypeDescription).getValue(annotationMethod).resolve();
                        if (pathValue != null) {
                            if (isClassLevelPath) {
                                transactionAnnotationValue.setClassLevelPath((String) pathValue);
                            } else {
                                transactionAnnotationValue.setMethodLevelPath((String) pathValue);
                            }
                        }
                    }
                    break;
                case "javax.ws.rs.GET":
                    transactionAnnotationValue.setMethodIfNotNull("GET");
                    break;
                case "javax.ws.rs.POST":
                    transactionAnnotationValue.setMethodIfNotNull("POST");
                    break;
                case "javax.ws.rs.PUT":
                    transactionAnnotationValue.setMethodIfNotNull("PUT");
                    break;
                case "javax.ws.rs.DELETE":
                    transactionAnnotationValue.setMethodIfNotNull("DELETE");
                    break;
                case "javax.ws.rs.HEAD":
                    transactionAnnotationValue.setMethodIfNotNull("HEAD");
                    break;
                case "javax.ws.rs.OPTIONS":
                    transactionAnnotationValue.setMethodIfNotNull("OPTIONS");
                    break;
                case "javax.ws.rs.HttpMethod":
                    transactionAnnotationValue.setMethodIfNotNull("HttpMethod");
                    break;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface JaxRsPath {

    }

    public static class TransactionAnnotationValue {

        private String classLevelPath = "";
        private String method = "";
        private String methodLevelPath = "";

        private void setClassLevelPath(String value) {
            this.classLevelPath = "/" + value;
        }

        private void setMethodLevelPath(String value) {
            this.methodLevelPath += "/" + value;
        }

        private void setMethodIfNotNull(@Nullable String value) {
            if (value == null) {
                return;
            }
            this.method = value;
        }

        String buildTransactionName() {
            String transactionName = (this.method + " " + this.classLevelPath + this.methodLevelPath);
            return transactionName.replaceAll("/+", "/");
        }
    }
}
