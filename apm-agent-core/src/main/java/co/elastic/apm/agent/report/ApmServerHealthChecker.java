/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.util.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

class ApmServerHealthChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);

    private final ReporterConfiguration reporterConfiguration;

    ApmServerHealthChecker(ReporterConfiguration reporterConfiguration) {
        this.reporterConfiguration = reporterConfiguration;
    }

    @Override
    public void run() {
        boolean success;
        String message;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(reporterConfiguration.getServerUrls().get(0).toString() + "/");
            if (logger.isDebugEnabled()) {
                logger.debug("Starting healthcheck to {}", url);
            }
            connection = (HttpURLConnection) url.openConnection();
            if (!reporterConfiguration.isVerifyServerCert()) {
                if (connection instanceof HttpsURLConnection) {
                    trustAll((HttpsURLConnection) connection);
                }
            }
            if (reporterConfiguration.getSecretToken() != null) {
                connection.setRequestProperty("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
            }
            connection.setRequestProperty("User-Agent", "java-agent/" + VersionUtils.getAgentVersion());
            connection.setConnectTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
            connection.setReadTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
            connection.connect();

            final int status = connection.getResponseCode();

            success = status < 300;

            if (!success) {
                if (status == 404) {
                    message = "It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                        "Please use APM Server 6.5.0 or newer.";
                } else {
                    message = Integer.toString(status);
                }
            } else  {
                // prints out the version info of the APM Server
                message = HttpUtils.getBody(connection);
            }
        } catch (IOException e) {
            message = e.getMessage();
            success = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (success) {
            logger.info("Elastic APM server is available: {}", message);
        } else {
            logger.warn("Elastic APM server is not available ({})", message);
        }
    }

    private void trustAll(HttpsURLConnection connection) {
        final SSLSocketFactory sf = SslUtils.getTrustAllSocketFactory();
        if (sf != null) {
            // using the same instances is important for TCP connection reuse
            connection.setHostnameVerifier(SslUtils.getTrustAllHostnameVerifyer());
            connection.setSSLSocketFactory(sf);
        }
    }
}
