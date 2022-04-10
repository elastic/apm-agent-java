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
package co.elastic.apm.agent.httpserver;

import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Objects;

public class HttpServerHelper {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerHelper.class);

    private final WebConfiguration configuration;

    public HttpServerHelper(WebConfiguration configuration){
        this.configuration = configuration;
    }

    public boolean isRequestExcluded(String path, @Nullable String userAgentHeader){
        final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(configuration.getIgnoreUrls(), path);
        if (excludeUrlMatcher != null && logger.isDebugEnabled()) {
            logger.debug("Not tracing this request as the path {} is ignored by the matcher {}",
                path, excludeUrlMatcher);
        }
        final WildcardMatcher excludeAgentMatcher = userAgentHeader != null ? WildcardMatcher.anyMatch(configuration.getIgnoreUserAgents(), userAgentHeader) : null;
        if (excludeAgentMatcher != null) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}",
                userAgentHeader, excludeAgentMatcher);
        }
        boolean isExcluded = excludeUrlMatcher != null || excludeAgentMatcher != null;
        if (!isExcluded && logger.isTraceEnabled()) {
            logger.trace("No matcher found for excluding this request with path: {} and User-Agent: {}",
                path, userAgentHeader);
        }
        return isExcluded;
    }
}
