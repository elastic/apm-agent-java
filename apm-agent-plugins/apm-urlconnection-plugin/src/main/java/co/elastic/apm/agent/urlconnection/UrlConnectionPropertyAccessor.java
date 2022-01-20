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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.List;

public class UrlConnectionPropertyAccessor implements TextHeaderSetter<HttpURLConnection>, TextHeaderGetter<HttpURLConnection> {

    private static final UrlConnectionPropertyAccessor INSTANCE = new UrlConnectionPropertyAccessor();

    private static final Logger logger = LoggerFactory.getLogger(UrlConnectionPropertyAccessor.class);

    public static UrlConnectionPropertyAccessor instance() {
        return INSTANCE;
    }

    @Override
    public void setHeader(String headerName, String headerValue, HttpURLConnection urlConnection) {
        try {
            urlConnection.addRequestProperty(headerName, headerValue);
        } catch (IllegalStateException e) {
            // Indicating that it is too late now to add request properties, see sun.net.www.protocol.http.HttpURLConnection#connecting
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to add header to the request", e);
            } else if (logger.isDebugEnabled()) {
                logger.debug("Failed to add header {} to the request through HttpUrlConnection: {}", headerName, e.getMessage());
            }
        }
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpURLConnection urlConnection) {
        return urlConnection.getRequestProperty(headerName);
    }

    @Override
    public <S> void forEach(String headerName, HttpURLConnection carrier, S state, HeaderConsumer<String, S> consumer) {
        List<String> values = carrier.getRequestProperties().get(headerName);
        if (values != null) {
            for (int i = 0, size = values.size(); i < size; i++) {
                consumer.accept(values.get(i), state);
            }
        }
    }
}
