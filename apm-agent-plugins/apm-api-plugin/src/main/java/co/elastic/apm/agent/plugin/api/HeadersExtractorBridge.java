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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;

@VisibleForAdvice
public class HeadersExtractorBridge implements TextHeaderGetter<Object> {

    private static final Logger logger = LoggerFactory.getLogger(HeadersExtractorBridge.class);

    private static final HeadersExtractorBridge INSTANCE = new HeadersExtractorBridge();

    @VisibleForAdvice
    public static HeadersExtractorBridge instance() {
        return INSTANCE;
    }

    @Nullable
    private MethodHandle getAllHeadersMethod;

    private HeadersExtractorBridge() {
    }

    @VisibleForAdvice
    public void setGetHeaderMethodHandle(MethodHandle getAllHeadersMethodHandle) {
        // No need to make thread-safe - only one methodHandle can be set in practice, so we don't care replacing its
        // reference a couple of times on startup. Cheaper than volatile access.
        if (this.getAllHeadersMethod == null) {
            this.getAllHeadersMethod = getAllHeadersMethodHandle;
        }
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Object carrier) {
        String value = null;
        try {
            if (getAllHeadersMethod != null) {
                //noinspection unchecked
                final Iterable<String> headersIterable = (Iterable<String>) getAllHeadersMethod.invoke(carrier, headerName);
                if (headersIterable != null) {
                    final Iterator<String> headersIterator = headersIterable.iterator();
                    if (headersIterator.hasNext()) {
                        value = headersIterator.next();
                    }
                }
            }
        } catch (Throwable throwable) {
            logger.error("Failed to extract trace context headers", throwable);
        }
        return value;
    }

    @Nullable
    @Override
    public Iterable<String> getHeaders(String headerName, Object carrier) {
        Iterable<String> values = null;
        if (getAllHeadersMethod != null) {
            try {
                //noinspection unchecked
                values = (Iterable<String>) getAllHeadersMethod.invoke(carrier, headerName);
            } catch (Throwable throwable) {
                logger.error("Failed to extract trace context headers", throwable);
            }
        }
        return values;
    }
}
