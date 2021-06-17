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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

public class ServletTransactionCreationHelper {

    private static final Logger logger = LoggerFactory.getLogger(ServletTransactionCreationHelper.class);

    private final Tracer tracer;
    private final WebConfiguration webConfiguration;

    public ServletTransactionCreationHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    @Nullable
    public Transaction createAndActivateTransaction(HttpServletRequest request) {
        Transaction transaction = null;
        // only create a transaction if there is not already one
        if (tracer.currentTransaction() == null &&
            !isExcluded(request.getServletPath(), request.getPathInfo(), request.getHeader("User-Agent"))) {
            ClassLoader cl = getClassloader(request.getServletContext());
            transaction = tracer.startChildTransaction(request, ServletRequestHeaderGetter.getInstance(), cl);
            if (transaction != null) {
                transaction.activate();
            }
        }
        return transaction;
    }

    private boolean isExcluded(String servletPath, @Nullable String pathInfo, @Nullable String userAgentHeader) {
        final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), servletPath, pathInfo);
        if (excludeUrlMatcher != null && logger.isDebugEnabled()) {
            logger.debug("Not tracing this request as the URL {}{} is ignored by the matcher {}",
                servletPath, Objects.toString(pathInfo, ""), excludeUrlMatcher);
        }
        final WildcardMatcher excludeAgentMatcher = userAgentHeader != null ? WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgentHeader) : null;
        if (excludeAgentMatcher != null) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}",
                userAgentHeader, excludeAgentMatcher);
        }
        boolean isExcluded = excludeUrlMatcher != null || excludeAgentMatcher != null;
        if (!isExcluded && logger.isTraceEnabled()) {
            logger.trace("No matcher found for excluding this request with servlet-path: {}, path-info: {} and User-Agent: {}",
                servletPath, pathInfo, userAgentHeader);
        }
        return isExcluded;
    }

    @Nullable
    public ClassLoader getClassloader(@Nullable ServletContext servletContext){
        if(servletContext == null) {
            return null;
        }

        // getClassloader might throw UnsupportedOperationException
        // see Section 4.4 of the Servlet 3.0 specification
        ClassLoader classLoader = null;
        try {
            return servletContext.getClassLoader();
        } catch (UnsupportedOperationException e) {
            // silently ignored
            return null;
        }
    }
}
