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
package co.elastic.apm.agent.report.ssl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

// based on https://gist.github.com/mefarazath/c9b588044d6bffd26aac3c520660bf40
public class SslUtils {

    private static final Logger logger = LoggerFactory.getLogger(SslUtils.class);

    private static final HostnameVerifier hostnameVerifier;

    private static boolean warningLogged = false;

    @Nullable
    private static final SSLSocketFactory validateSocketFactory;

    @Nullable
    private static final SSLSocketFactory trustAllSocketFactory;

    static {
        // default factory with certificate validation
        validateSocketFactory = TLSFallbackSSLSocketFactory.wrapFactory(createSocketFactory(null));

        SSLSocketFactory tmpTrustAllSocketFactory = null;
        // without certificate validation
        try {
            X509TrustManager trustAllTrustManager = createTrustAllTrustManager();
            tmpTrustAllSocketFactory = TLSFallbackSSLSocketFactory.wrapFactory(createSocketFactory(new TrustManager[]{trustAllTrustManager}));
        } catch (Exception e) {
            logger.info("Failed to construct a trust-all Socket factory with the following error: \"{}\". Agent communication " +
                "with the APM server must verify the server certificates, meaning - the \"verify_server_cert\" configuration " +
                "option must be set to \"true\"", e.getMessage());
        }
        trustAllSocketFactory = tmpTrustAllSocketFactory;

        hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
    }

    public static HostnameVerifier getTrustAllHostnameVerifyer() {
        return hostnameVerifier;
    }

    @Nullable
    public static SSLSocketFactory getSSLSocketFactory(boolean validateCertificates) {
        if (validateCertificates) {
            return validateSocketFactory;
        }
        if (trustAllSocketFactory == null && !warningLogged) {
            logger.warn("The \"verify_server_cert\" configuration option is set to \"false\", but this agent may not be " +
                "able to communicate with APM Server without verifying the server certificates.");
            warningLogged = true;
        }
        return trustAllSocketFactory;
    }

    @Nullable
    private static SSLSocketFactory createSocketFactory(TrustManager[] trustAllCerts) {
        try {
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("SSL");
            } catch (NoSuchAlgorithmException e) {
                logger.info("SSL is not supported, trying to use TLS instead.");
                sslContext = SSLContext.getInstance("TLS");
            }
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.warn(e.getMessage(), e);
            return null;
        }
    }

    private static X509TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

}
