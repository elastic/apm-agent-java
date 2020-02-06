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

@VisibleForAdvice
public class HeaderExtractorBridge implements TextHeaderGetter<Object> {

    private static final Logger logger = LoggerFactory.getLogger(HeaderExtractorBridge.class);

    private static final HeaderExtractorBridge INSTANCE = new HeaderExtractorBridge();

    @VisibleForAdvice
    public static HeaderExtractorBridge instance() {
        return INSTANCE;
    }

    @Nullable
    private MethodHandle getFirstHeaderMethod;

    private HeaderExtractorBridge() {
    }

    @VisibleForAdvice
    public void setGetHeaderMethodHandle(MethodHandle getHeaderMethodHandle) {
        // No need to make thread-safe - only one methodHandle can be set in practice, so we don't care replacing its
        // reference a couple of times on startup. Cheaper than volatile access.
        if (this.getFirstHeaderMethod == null) {
            this.getFirstHeaderMethod = getHeaderMethodHandle;
        }
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Object carrier) {
        String value = null;
        try {
            if (getFirstHeaderMethod != null) {
                value = (String) getFirstHeaderMethod.invoke(carrier, headerName);
            }
        } catch (Throwable throwable) {
            logger.error("Failed to extract trace context headers", throwable);
        }
        return value;
    }

    /**
     * Returns null. {@link HeadersExtractorBridge} should be used instead for this functionality
     */
    @Nullable
    @Override
    public Iterable<String> getHeaders(String headerName, Object carrier) {
        return null;
    }
}
