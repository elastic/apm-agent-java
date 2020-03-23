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
package co.elastic.apm.agent.grpc;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;

public class GrpcTestNameGenerator extends DisplayNameGenerator.Standard {

    private static final String ROOT_PACKAGE = GrpcTestNameGenerator.class.getPackageName();

    @Override
    public String generateDisplayNameForClass(Class<?> testClass) {
        return getNameWithVersion(testClass, super.generateDisplayNameForClass(testClass));
    }

    @Override
    public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
        return getNameWithVersion(nestedClass, super.generateDisplayNameForNestedClass(nestedClass));
    }

    @Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        return getNameWithVersion(testClass, super.generateDisplayNameForMethod(testClass, testMethod));
    }

    private static String getNameWithVersion(Class<?> type, String defaultValue) {
        String classPackage = type.getPackageName();
        String version = null;
        if (classPackage.startsWith(ROOT_PACKAGE)) {
            String packageSuffix = classPackage.substring(ROOT_PACKAGE.length() + 1);
            int firstDot = packageSuffix.indexOf('.');
            if (firstDot <= 0) {
                version = packageSuffix;
            } else {
                version = packageSuffix.substring(0, firstDot);
            }
        }
        return version != null ? String.format("%s / %s", version, defaultValue) : defaultValue;
    }
}
