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
package co.elastic.apm.agent.report;

import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;

public class HttpUtils {

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
            IOUtils.consumeAndClose(connection.getErrorStream());
            try {
                IOUtils.consumeAndClose(connection.getInputStream());
            } catch (IOException ignored) {
                // silently ignored
            }
        }
    }

    /**
     * Rebuilds the provided URL with the default handler, as it may have been overriden by an application server at
     * runtime and might prevent the agent from properly communicate through HTTP/HTTPS.
     *
     * @param url URL to rewrite
     * @return equivalent URL with the default JDK handler
     * @throws IllegalArgumentException if protocol is not supported or unable to access default handler
     */
    public static URL withDefaultHandler(URL url) {
        // the default handler for URLs might be overridden by another implementation than the one shipped with the JDK
        // for example, this happens on Weblogic application server and triggers classloading issues as the agent
        // is unable to properly use the Weblogic classes as they aren't visible to the agent classloaders.

        String protocol = url.getProtocol();
        try {
            return new URL(protocol, url.getHost(), url.getPort(), url.getFile(), handlerForProtocol(protocol));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static URLStreamHandler handlerForProtocol(String protocol) {
        try {
            Class<?> handlerClass = Class.forName(String.format("sun.net.www.protocol.%s.Handler", protocol));
            return (URLStreamHandler) handlerClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("unable to retrieve handler for protocol : " + protocol);
        }
    }
}
