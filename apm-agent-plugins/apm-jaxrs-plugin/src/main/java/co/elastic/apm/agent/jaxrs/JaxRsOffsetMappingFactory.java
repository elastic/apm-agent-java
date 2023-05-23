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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class JaxRsOffsetMappingFactory implements Advice.OffsetMapping.Factory<JaxRsOffsetMappingFactory.JaxRsPath> {

    public static boolean useAnnotationValueForTransactionName;

    public JaxRsOffsetMappingFactory(Tracer tracer) {
        useAnnotationValueForTransactionName = tracer.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName();
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
                if (useAnnotationValueForTransactionName) {
                    value = getTransactionAnnotationValueFromAnnotations(instrumentedMethod, instrumentedType);
                }
                return Target.ForStackManipulation.of(value);
            }
        };
    }

    private String getTransactionAnnotationValueFromAnnotations(MethodDescription instrumentedMethod, TypeDescription instrumentedType) {
        TransactionAnnotationValue transactionAnnotationValue = new TransactionAnnotationValue();
        String methodName = instrumentedMethod.getName();
        while (!transactionAnnotationValue.isComplete() && !"java.lang.Object".equals(instrumentedType.getCanonicalName())) {
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
            getAnnotationValueFromAnnotationSource(transactionAnnotationValue, interfaceDescription, true);
            for (MethodDescription.InDefinedShape annotationMethod : interfaceDescription.getDeclaredMethods().filter(named(methodName))) {
                getAnnotationValueFromAnnotationSource(transactionAnnotationValue, annotationMethod, false);
            }
            findInInterfaces(transactionAnnotationValue, interfaceDescription, methodName);
        }
    }

    private void getAnnotationValueFromAnnotationSource(TransactionAnnotationValue transactionAnnotationValue, AnnotationSource annotationSource, Boolean isClassLevelPath) {
        for (TypeDescription classMethodTypeDescription : annotationSource.getDeclaredAnnotations().asTypeList()) {
            String canonicalName = classMethodTypeDescription.getCanonicalName();
            switch (canonicalName) {
                case "jakarta.ws.rs.Path":
                case "javax.ws.rs.Path":
                    String pathValue = getAnnotationValue(annotationSource, classMethodTypeDescription, "value");
                    if (isClassLevelPath) {
                        transactionAnnotationValue.setClassLevelPath(pathValue);
                    } else {
                        transactionAnnotationValue.setMethodLevelPath(pathValue);
                    }
                    break;
                case "jakarta.ws.rs.GET":
                case "javax.ws.rs.GET":
                    transactionAnnotationValue.setMethodIfNotNull("GET");
                    break;
                case "jakarta.ws.rs.POST":
                case "javax.ws.rs.POST":
                    transactionAnnotationValue.setMethodIfNotNull("POST");
                    break;
                case "jakarta.ws.rs.PUT":
                case "javax.ws.rs.PUT":
                    transactionAnnotationValue.setMethodIfNotNull("PUT");
                    break;
                case "jakarta.ws.rs.DELETE":
                case "javax.ws.rs.DELETE":
                    transactionAnnotationValue.setMethodIfNotNull("DELETE");
                    break;
                case "jakarta.ws.rs.HEAD":
                case "javax.ws.rs.HEAD":
                    transactionAnnotationValue.setMethodIfNotNull("HEAD");
                    break;
                case "jakarta.ws.rs.OPTIONS":
                case "javax.ws.rs.OPTIONS":
                    transactionAnnotationValue.setMethodIfNotNull("OPTIONS");
                    break;
            }
        }
    }

    @Nullable
    private <T> T getAnnotationValue(AnnotationSource annotationSource, TypeDescription annotationType, String method) {
        for (MethodDescription.InDefinedShape annotationMethod : annotationType.getDeclaredMethods().filter(named(method))) {
            return (T) annotationSource.getDeclaredAnnotations().ofType(annotationType).getValue(annotationMethod).resolve();
        }
        return null;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface JaxRsPath {

    }

    public static class TransactionAnnotationValue {

        private String classLevelPath = "";
        private String method = "";
        private String methodLevelPath = "";

        private void setClassLevelPath(@Nullable String value) {
            if (value != null && classLevelPath.isEmpty()) {
                this.classLevelPath = ensureStartsWithSlash(value);
            }
        }

        private void setMethodLevelPath(@Nullable String value) {
            if (value != null && methodLevelPath.isEmpty()) {
                this.methodLevelPath = ensureStartsWithSlash(value);
            }
        }

        private void setMethodIfNotNull(@Nullable String value) {
            if (value != null && method.isEmpty()) {
                this.method = value;
            }
        }

        @Nonnull
        private String ensureStartsWithSlash(String value) {
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            if (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }

        String buildTransactionName() {
            String path = this.classLevelPath + this.methodLevelPath;
            return this.method + " " + ((path.isEmpty()) ? "/": path);
        }

        public boolean isComplete() {
            return !method.isEmpty() && !classLevelPath.isEmpty() && !methodLevelPath.isEmpty();
        }
    }
}
