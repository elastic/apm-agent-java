/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.report;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpUtilsTest {

    @Test
    void consumeAndCloseIgnoresNullConnection() {
        HttpUtils.consumeAndClose(null);
    }

    @Test
    void consumeAndCloseNoStreams() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getErrorStream()).thenReturn(null);
        when(connection.getInputStream()).thenReturn(null);

        HttpUtils.consumeAndClose(connection);
    }

    @Test
    void consumeAndCloseException() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);

        InputStream errorStream = mockEmptyInputStream();
        when(connection.getErrorStream()).thenReturn(errorStream);

        when(connection.getInputStream()).thenThrow(IOException.class);

        HttpUtils.consumeAndClose(connection);

        verify(errorStream).close();
    }

    @Test
    void consumeAndCloseResponseContent() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);

        when(connection.getErrorStream()).thenReturn(null);
        InputStream responseStream = mockEmptyInputStream();

        when(connection.getInputStream()).thenReturn(responseStream);

        HttpUtils.consumeAndClose(connection);

        verify(responseStream).close();
    }

    private static InputStream mockEmptyInputStream() throws IOException {
        // very partial mock, but enough for what we want to test
        InputStream stream = mock(InputStream.class);
        when(stream.available()).thenReturn(0);
        when(stream.read()).thenReturn(-1);
        when(stream.read(any())).thenReturn(-1);
        return stream;
    }

}
