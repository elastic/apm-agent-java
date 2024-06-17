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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.report.processor.Processor;
import co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.agent.impl.context.AbstractContextImpl.REDACTED_CONTEXT_STRING;

/**
 * Sanitizes web-related fields according to the {@link CoreConfigurationImpl#sanitizeFieldNames} setting
 */
public class SanitizingWebProcessor implements Processor {

    private final CoreConfigurationImpl config;

    public SanitizingWebProcessor(ConfigurationRegistry configurationRegistry) {
        config = configurationRegistry.getConfig(CoreConfigurationImpl.class);
    }

    @Override
    public void processBeforeReport(TransactionImpl transaction) {
        sanitizeContext(transaction.getContext());
    }

    @Override
    public void processBeforeReport(ErrorCaptureImpl error) {
        sanitizeContext(error.getContext());
    }

    private void sanitizeContext(TransactionContextImpl context) {
        sanitizeRequest(context.getRequest());
        sanitizeMap(context.getResponse().getHeaders());
    }

    private void sanitizeRequest(RequestImpl request) {
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
