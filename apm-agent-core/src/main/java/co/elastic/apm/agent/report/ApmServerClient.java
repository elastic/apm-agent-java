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

import co.elastic.apm.agent.util.VersionUtils;

import javax.annotation.Nonnull;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ApmServerClient {

    private static final String USER_AGENT = "elasticapm-java/" + VersionUtils.getAgentVersion();
    private final ReporterConfiguration reporterConfiguration;
    private final CyclicIterator<URL> serverUrlIterator;

    public ApmServerClient(ReporterConfiguration reporterConfiguration) {
        this(reporterConfiguration, shuffleUrls(reporterConfiguration));
    }

    public ApmServerClient(ReporterConfiguration reporterConfiguration, List<URL> serverUrls) {
        this.reporterConfiguration = reporterConfiguration;
        this.serverUrlIterator = new CyclicIterator<>(serverUrls);
    }

    private static List<URL> shuffleUrls(ReporterConfiguration reporterConfiguration) {
        List<URL> serverUrls = new ArrayList<>(reporterConfiguration.getServerUrls());
        // shuffling the URL list helps to distribute the load across the apm servers
        // when there are multiple agents, they should not all start connecting to the same apm server
        Collections.shuffle(serverUrls);
        return serverUrls;
    }

    private static void trustAll(HttpsURLConnection connection) {
        final SSLSocketFactory sf = SslUtils.getTrustAllSocketFactory();
        if (sf != null) {
            // using the same instances is important for TCP connection reuse
            connection.setHostnameVerifier(SslUtils.getTrustAllHostnameVerifyer());
            connection.setSSLSocketFactory(sf);
        }
    }

    public HttpURLConnection startRequest(String path) throws IOException {
        URL url = getUrl(path);
        final URLConnection connection = url.openConnection();
        if (!reporterConfiguration.isVerifyServerCert()) {
            if (connection instanceof HttpsURLConnection) {
                trustAll((HttpsURLConnection) connection);
            }
        }
        if (reporterConfiguration.getSecretToken() != null) {
            connection.setRequestProperty("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
        }
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        connection.setReadTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        return (HttpURLConnection) connection;
    }

    @Nonnull
    URL getUrl(String apmServerPath) throws MalformedURLException {
        URL serverUrl = serverUrlIterator.get();
        String path = serverUrl.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return new URL(serverUrl, path + apmServerPath);
    }

    public void switchToNextServerUrl() {
        serverUrlIterator.next();
    }

    private static class CyclicIterator<T> {
        private final Iterable<T> iterable;
        private Iterator<T> iterator;
        private T current;

        public CyclicIterator(Iterable<T> iterable) {
            this.iterable = iterable;
            iterator = this.iterable.iterator();
            current = iterator.next();
        }

        public synchronized T get() {
            return current;
        }

        public synchronized void next() {
            if (!iterator.hasNext()) {
                iterator = iterable.iterator();
            }
            current = iterator.next();
        }
    }
}
