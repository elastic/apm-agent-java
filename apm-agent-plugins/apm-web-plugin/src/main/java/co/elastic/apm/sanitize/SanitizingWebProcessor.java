/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.sanitize;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.report.processor.Processor;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Map;

/**
 * Sanitizes web-related fields according to the {@link CoreConfiguration#sanitizeFieldNames} setting
 */
public class SanitizingWebProcessor implements Processor {

    static final String REDACTED = "[REDACTED]";
    private CoreConfiguration config;

    @Override
    public void init(ConfigurationRegistry configurationRegistry) {
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

    private void sanitizeContext(Context context) {
        sanitizeRequest(context.getRequest());
        sanitizeMap(context.getResponse().getHeaders());
    }

    private void sanitizeRequest(Request request) {
        sanitizeMap(request.getHeaders());
        // cookies are stored in Request.cookies
        // storing it twice would be wasteful
        // also, sanitizing the cookie header value as a string is difficult
        // when you don't want to create garbage
        removeCookieHeader(request.getHeaders());
        sanitizeMap(request.getFormUrlEncodedParameters());
        sanitizeMap(request.getCookies());
    }

    private void sanitizeMap(Map<String, ? super String> map) {
        for (Map.Entry<String, ? super String> entry : map.entrySet()) {
            if (isSensitive(entry.getKey())) {
                entry.setValue(REDACTED);
            }
        }
    }

    private void removeCookieHeader(PotentiallyMultiValuedMap<String, String> headers) {
        for (String headerName : headers.keySet()) {
            if ("Cookie".equalsIgnoreCase(headerName)) {
                headers.remove(headerName);
            }
        }
    }

    private boolean isSensitive(String key) {
        return WildcardMatcher.anyMatch(config.getSanitizeFieldNames(), key);
    }
}
