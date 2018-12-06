/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {

    }

    public static String getBody(HttpURLConnection connection) {
        String body;
        try {
            if (connection == null || connection.getInputStream() == null)
                return null;
            body = readInputStream(connection.getInputStream());
            return body;
        } catch (final IOException e) {
            logger.error("Reading inputStream: {}", e.getMessage());
            try {
                body = readInputStream(connection.getErrorStream());
                return body;
            } catch (IOException e1) {
                logger.error("Reading errorStream: {}", e1.getMessage());
            }
        }
        return null;
    }

    private static String readInputStream(final InputStream inputStream) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        final StringBuilder bodyString = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            bodyString.append(line);
        }
        bufferedReader.close();
        return bodyString.toString();
    }

}
