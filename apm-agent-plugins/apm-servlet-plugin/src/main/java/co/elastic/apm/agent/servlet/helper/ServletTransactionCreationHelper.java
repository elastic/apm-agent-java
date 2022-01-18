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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;

public abstract class ServletTransactionCreationHelper<HTTPREQUEST, CONTEXT> {

    private static final Logger logger = LoggerFactory.getLogger(ServletTransactionCreationHelper.class);

    private final Tracer tracer;
    private final WebConfiguration webConfiguration;

    public ServletTransactionCreationHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    @Nullable
    public Transaction createAndActivateTransaction(HTTPREQUEST request) {
        // only create a transaction if there is not already one
        if (tracer.currentTransaction() != null) {
            return null;
        }
        if (isExcluded(request)) {
            return null;
        }
        ClassLoader cl = getClassloader(getServletContext(request));
        Transaction transaction = tracer.startChildTransaction(request, getRequestHeaderGetter(), cl);
        if (transaction != null) {
            transaction.activate();
        }
        return transaction;
    }

    protected abstract String getServletPath(HTTPREQUEST request);

    protected abstract String getPathInfo(HTTPREQUEST request);

    protected abstract String getHeader(HTTPREQUEST request, String headerName);

    protected abstract CONTEXT getServletContext(HTTPREQUEST request);

    protected abstract ClassLoader getClassLoader(CONTEXT servletContext);

    protected abstract CommonServletRequestHeaderGetter getRequestHeaderGetter();

    protected abstract String getContextPath(HTTPREQUEST request);

    protected abstract String getRequestURI(HTTPREQUEST request);

    boolean isExcluded(HTTPREQUEST request) {
        String userAgent = getHeader(request, "User-Agent");
        String requestUri = getRequestURI(request);

        final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), requestUri);

        if (excludeUrlMatcher != null && logger.isDebugEnabled()) {
            logger.debug("Not tracing this request as the URL {} is ignored by the matcher {}", requestUri, excludeUrlMatcher);
        }
        final WildcardMatcher excludeAgentMatcher = userAgent != null ? WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgent) : null;
        if (excludeAgentMatcher != null) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}", userAgent, excludeAgentMatcher);
        }
        boolean isExcluded = excludeUrlMatcher != null || excludeAgentMatcher != null;
        if (!isExcluded && logger.isTraceEnabled()) {
            logger.trace("No matcher found for excluding this request with URL: {}, and User-Agent: {}", requestUri, userAgent);
        }
        return isExcluded;
    }

    @Nullable
    public ClassLoader getClassloader(@Nullable CONTEXT servletContext) {
        if (servletContext == null) {
            return null;
        }

        // getClassloader might throw UnsupportedOperationException
        // see Section 4.4 of the Servlet 3.0 specification
        ClassLoader classLoader = null;
        try {
            return getClassLoader(servletContext);
        } catch (UnsupportedOperationException e) {
            // silently ignored
            return null;
        }
    }
}
