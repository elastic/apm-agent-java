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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.processor.Processor;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;

/**
 * Sanitizes web-related fields according to the {@link CoreConfiguration#sanitizeFieldNames} setting
 */
public class SanitizingWebProcessor implements Processor {

    private final CoreConfiguration config;

    public SanitizingWebProcessor(ConfigurationRegistry configurationRegistry) {
        config = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    @Override
    public void processBeforeReport(Transaction transaction) {
        sanitizeContext(transaction.getContext());
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        sanitizeContext(error.getContext());
    }

    private void sanitizeContext(TransactionContext context) {
        sanitizeRequest(context.getRequest());
        sanitizeMap(context.getResponse().getHeaders());
    }

    private void sanitizeRequest(Request request) {
        sanitizeMap(request.getHeaders());
        // cookies are stored in Request.cookies
        // storing it twice would be wasteful
        // also, sanitizing the cookie header value as a string is difficult
        // when you don't want to create garbage
        request.getHeaders().removeIgnoreCase("Cookie");
        sanitizeMap(request.getFormUrlEncodedParameters());
        sanitizeMap(request.getCookies());
    }

    private void sanitizeMap(PotentiallyMultiValuedMap map) {
        for (int i = 0; i < map.size(); i++) {
            if (isSensitive(map.getKey(i))) {
                map.set(i, REDACTED_CONTEXT_STRING);
            }
        }
    }

    private boolean isSensitive(String key) {
        assert config != null;
        return WildcardMatcher.anyMatch(config.getSanitizeFieldNames(), key) != null;
    }
}
