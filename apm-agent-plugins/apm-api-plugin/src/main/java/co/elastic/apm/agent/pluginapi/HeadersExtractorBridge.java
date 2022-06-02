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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;

public class HeadersExtractorBridge implements TextHeaderGetter<HeadersExtractorBridge.Extractor> {

    private static final Logger logger = LoggerFactory.getLogger(HeadersExtractorBridge.class);
    @Nullable
    private static HeadersExtractorBridge INSTANCE;

    public static class Extractor {

        @Nullable
        private final Object headerExtractor;
        private final Object headersExtractor;

        private Extractor(@Nullable Object headerExtractor, Object headersExtractor) {
            this.headerExtractor = headerExtractor;
            this.headersExtractor = headersExtractor;
        }

        public static Extractor of(@Nullable Object headerExtractor, Object headersExtractor) {
            return new Extractor(headerExtractor, headersExtractor);
        }
    }

    private final MethodHandle getFirstHeaderMethod;
    private final MethodHandle getAllHeadersMethod;

    public static HeadersExtractorBridge get(MethodHandle getFirstHeaderMethod, MethodHandle getAllHeadersMethod) {
        if (INSTANCE == null) {
            INSTANCE = new HeadersExtractorBridge(getFirstHeaderMethod, getAllHeadersMethod);
        }
        return INSTANCE;
    }

    private HeadersExtractorBridge(MethodHandle getFirstHeaderMethod, MethodHandle getAllHeadersMethod) {
        this.getFirstHeaderMethod = getFirstHeaderMethod;
        this.getAllHeadersMethod = getAllHeadersMethod;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Extractor carrier) {
        if (carrier.headerExtractor != null) {
            try {
                return (String) getFirstHeaderMethod.invoke(carrier.headerExtractor, headerName);
            } catch (Throwable throwable) {
                logger.error("Failed to extract trace context headers", throwable);
            }
        } else {
            Iterable<String> headers = getValues(headerName, carrier);
            if (headers != null) {
                Iterator<String> iterator = headers.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, Extractor carrier, S state, HeaderConsumer<String, S> consumer) {
        Iterable<String> values = getValues(headerName, carrier);
        if (values != null) {
            for (String value : values) {
                consumer.accept(value, state);
            }
        }
    }

    @Nullable
    public Iterable<String> getValues(String headerName, Extractor carrier) {
        Iterable<String> values = null;
        try {
            //noinspection unchecked
            values = (Iterable<String>) getAllHeadersMethod.invoke(carrier.headersExtractor, headerName);
        } catch (Throwable throwable) {
            logger.error("Failed to extract trace context headers", throwable);
        }
        return values;
    }
}
