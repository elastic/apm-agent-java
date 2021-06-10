/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.bci.methodmatching.configuration;

import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.configuration.converter.ListValueConverter;

import org.stagemonitor.configuration.converter.ValueConverter;

import static co.elastic.apm.agent.configuration.converter.ListValueConverter.COMMA_OUT_OF_BRACKETS;

public enum MethodMatcherValueConverter implements ValueConverter<MethodMatcher> {
    INSTANCE;

    @Override
    public MethodMatcher convert(String methodMatcher) throws IllegalArgumentException {
        return MethodMatcher.of(methodMatcher);
    }

    @Override
    public String toString(MethodMatcher methodMatcher) {
        return methodMatcher.toString();
    }

    @Override
    public String toSafeString(MethodMatcher value) {
        return toString(value);
    }

    public static final ListValueConverter<MethodMatcher> LIST =
        new ListValueConverter<>(INSTANCE, COMMA_OUT_OF_BRACKETS);
}
