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
                    TransactionAnnotationValue transactionAnnotationValue = getTransactionAnnotationValueFromAnnotations(instrumentedMethod, instrumentedType);
                    value= transactionAnnotationValue.buildTransactionName();
                }
                return Target.ForStackManipulation.of(value);
            }
        };
    }

    private TransactionAnnotationValue getTransactionAnnotationValueFromAnnotations(MethodDescription instrumentedMethod, TypeDescription instrumentedType) {
        TransactionAnnotationValue transactionAnnotationValue = new TransactionAnnotationValue();
        String methodName = instrumentedMethod.getName();
        while (transactionAnnotationValue.method == null && !"java.lang.Object".equals(instrumentedType.getCanonicalName())) {
            getAnnotationValueFromAnnotationSource(transactionAnnotationValue, instrumentedType);
            for (MethodDescription.InDefinedShape annotationMethod : instrumentedType.getDeclaredMethods().filter(named(methodName)).asDefined()) {
                getAnnotationValueFromAnnotationSource(transactionAnnotationValue, annotationMethod);
            }
            findInInterfaces(transactionAnnotationValue, instrumentedType, methodName);
            instrumentedType = instrumentedType.getSuperClass().asErasure();
        }
        return transactionAnnotationValue;
    }

    private void findInInterfaces(TransactionAnnotationValue transactionAnnotationValue, TypeDescription classTypeDescription, String methodName) {
        TypeList interfaces = classTypeDescription.getInterfaces().asErasures();
        for (int i = 0; transactionAnnotationValue.method == null && i < interfaces.size(); i++) {
            TypeDescription interfaceDescription = interfaces.get(i);
            getAnnotationValueFromAnnotationSource(transactionAnnotationValue, interfaceDescription);
            for (MethodDescription.InDefinedShape annotationMethod : interfaceDescription.getDeclaredMethods().filter(named(methodName))) {
                getAnnotationValueFromAnnotationSource(transactionAnnotationValue, annotationMethod);
            }
            findInInterfaces(transactionAnnotationValue, interfaceDescription, methodName);
        }
    }

    public void getAnnotationValueFromAnnotationSource(TransactionAnnotationValue transactionAnnotationValue, AnnotationSource annotationSource) {
        for (TypeDescription classMethodTypeDescription : annotationSource.getDeclaredAnnotations().asTypeList()) {
            String canonicalName = classMethodTypeDescription.getCanonicalName();
            switch (canonicalName) {
                case "javax.ws.rs.Path":
                    for (MethodDescription.InDefinedShape annotationMethod : classMethodTypeDescription.getDeclaredMethods().filter(named("value"))) {
                        Object pathValue = annotationSource.getDeclaredAnnotations().ofType(classMethodTypeDescription).getValue(annotationMethod).resolve();
                        if (pathValue != null) {
                            transactionAnnotationValue.appendToPath("/");
                            transactionAnnotationValue.appendToPath((String) pathValue);
                        }
                    }
                    break;
                case "javax.ws.rs.GET":
                    transactionAnnotationValue.method = "GET";
                    break;
                case "javax.ws.rs.POST":
                    transactionAnnotationValue.method = "POST";
                    break;
                case "javax.ws.rs.PUT":
                    transactionAnnotationValue.method = "PUT";
                    break;
                case "javax.ws.rs.DELETE":
                    transactionAnnotationValue.method = "DELETE";
                    break;
                case "javax.ws.rs.HEAD":
                    transactionAnnotationValue.method = "HEAD";
                    break;
                case "javax.ws.rs.OPTIONS":
                    transactionAnnotationValue.method = "OPTIONS";
                    break;
                case "javax.ws.rs.HttpMethod":
                    transactionAnnotationValue.method = "HttpMethod";
                    break;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface JaxRsPath {

    }

    public static class TransactionAnnotationValue {

        private String method;
        private StringBuilder path;

        public TransactionAnnotationValue() {
            this.path = new StringBuilder();
        }

        public void appendToPath(String newPath) {
            this.path.append(newPath);
        }

        String buildTransactionName() {
            StringBuilder signature = new StringBuilder();

            if (this.method != null) {
                signature.append(this.method).append(" ");
            }
            signature.append(this.path);

            return signature.toString();
        }
    }
}
