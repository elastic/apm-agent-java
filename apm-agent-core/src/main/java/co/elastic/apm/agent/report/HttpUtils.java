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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
    }

    /**
     * Reads the steam and converts the contents to a string, without closing stream.
     *
     * @param inputStream the input stream
     * @return the content of the stream as a string
     */
    public static String readToString(final InputStream inputStream) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        final StringBuilder bodyString = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            bodyString.append(line);
        }
        return bodyString.toString();
    }

    /**
     * In order to be able to reuse the underlying TCP connections,
     * the input stream must be consumed and closed
     * see also https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
     *
     * @param connection the connection
     */
    public static void consumeAndClose(@Nullable HttpURLConnection connection) {
        if (connection != null) {
            try {
                InputStream responseInputStream = connection.getErrorStream();
                if (responseInputStream == null) {
                    responseInputStream = connection.getInputStream();
                }
                if (responseInputStream != null) {
                    IOUtils.consumeAndClose(responseInputStream);
                }
            } catch (Exception e) {
                logger.error("Exception when closing input stream of HttpURLConnection.");
            }
        }
    }
}
