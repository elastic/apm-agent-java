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

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

class RequestHeaderGetter implements TextHeaderGetter<HttpServletRequest>, Iterable<String> {

    // Caching in ThreadLocal in order to reuse the Enumeration-based iterator
    private static final ThreadLocal<RequestHeaderGetter> threadLocalRequestHeaderGetter = new ThreadLocal<>();

    static RequestHeaderGetter getInstance() {
        RequestHeaderGetter requestHeaderGetter = threadLocalRequestHeaderGetter.get();
        if (requestHeaderGetter == null) {
            requestHeaderGetter = new RequestHeaderGetter();
            threadLocalRequestHeaderGetter.set(requestHeaderGetter);
        }
        return requestHeaderGetter;
    }

    private final HeaderValuesIterator headerValuesIterator = new HeaderValuesIterator();

    private RequestHeaderGetter() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpServletRequest request) {
        return request.getHeader(headerName);
    }

    @Nullable
    @Override
    public Iterable<String> getHeaders(String headerName, HttpServletRequest request) {
        headerValuesIterator.headerValues = request.getHeaders(headerName);
        return this;
    }

    @Override
    public Iterator<String> iterator() {
        return headerValuesIterator;
    }

    private static final class HeaderValuesIterator implements Iterator<String> {

        @Nullable
        private Enumeration<String> headerValues;

        @Override
        public boolean hasNext() {
            if (headerValues != null) {
                return headerValues.hasMoreElements();
            }
            return false;
        }

        @Override
        public String next() {
            if (headerValues != null) {
                return headerValues.nextElement();
            }
            throw new NoSuchElementException("Header values Enumeration is not initialized");
        }
    }
}
