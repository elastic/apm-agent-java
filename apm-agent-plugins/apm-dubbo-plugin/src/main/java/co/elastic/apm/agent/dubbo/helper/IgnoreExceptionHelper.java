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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.stagemonitor.configuration.ConfigurationOption;

import java.util.HashSet;
import java.util.Set;

public class IgnoreExceptionHelper {

    public static ElasticApmTracer tracer;

    public static final String IGNORE_EXCEPTIONS_CONF_KEY = "ignore_exceptions";

    public static final String IGNORE_EXCEPTIONS_PROP_KEY = "elastic.apm.ignore_exceptions";

    public static final String IGNORE_EXCEPTION_SEPARATOR = ",";

    public static void init(ElasticApmTracer tracer) {
        IgnoreExceptionHelper.tracer = tracer;
    }

    public static void addIgnoreException(Throwable t) {
        Set<String> ignoredExceptionSet = getIgnoredExceptionSet();
        if (!ignoredExceptionSet.add(t.getClass().getName())) {
            return;
        }

        refreshIgnoreExceptionConfig(ignoredExceptionSet);
    }

    public static void refreshIgnoreExceptionConfig(Set<String> ignoredExceptionSet) {
        System.setProperty(IGNORE_EXCEPTIONS_PROP_KEY, toConfigString(ignoredExceptionSet));
        tracer.getConfigurationRegistry().reload(IGNORE_EXCEPTIONS_CONF_KEY);
    }

    public static Set<String> getIgnoredExceptionSet() {
        ConfigurationOption<?> ignoreExceptionsConfigOpt =
            tracer.getConfigurationRegistry().getConfigurationOptionByKey(IGNORE_EXCEPTIONS_CONF_KEY);
        String ignoreExpStr = ignoreExceptionsConfigOpt.getValueAsString();
        Set<String> ignoredExceptionSet = new HashSet<>();
        if (ignoreExpStr != null) {
            String[] ignoreStrArr = ignoreExpStr.split(IGNORE_EXCEPTION_SEPARATOR);
            for (String ignoreStr : ignoreStrArr) {
                if (ignoreStr.length() > 0) {
                    ignoredExceptionSet.add(ignoreStr);
                }
            }
        }
        return ignoredExceptionSet;
    }

    public static String toConfigString(Set<String> ignoredExceptionSet) {
        if (ignoredExceptionSet.isEmpty()) {
            return "";
        }
        String[] ignoredExceptionArr = ignoredExceptionSet.toArray(new String[0]);
        StringBuilder configBuilder = new StringBuilder(ignoredExceptionArr[0]);
        for (int i = 1; i < ignoredExceptionArr.length; i++) {
            configBuilder.append(IGNORE_EXCEPTION_SEPARATOR).append(ignoredExceptionArr[i]);
        }
        return configBuilder.toString();
    }
}
