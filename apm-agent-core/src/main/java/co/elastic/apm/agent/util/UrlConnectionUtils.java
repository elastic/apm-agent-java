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
package co.elastic.apm.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class UrlConnectionUtils {

    private static final Logger logger = LoggerFactory.getLogger(UrlConnectionUtils.class);

    public static URLConnection openUrlConnectionThreadSafely(URL url) throws IOException {
        GlobalLocks.JUL_INIT_LOCK.lock();
        try {
            if (logger.isDebugEnabled()) {
                String proxyHostProperty = url.getProtocol() + ".proxyHost";
                String proxyPortProperty = url.getProtocol() + ".proxyPort";
                String proxyHost = System.getProperty(proxyHostProperty);
                String proxyPort = System.getProperty(proxyPortProperty);
                String nonProxyHosts = System.getProperty("http.nonProxyHosts"); // common to http & https
                if (proxyHost == null || proxyHost.isEmpty()) {
                    logger.debug("Opening {} without proxy", url);
                } else {
                    logger.debug("Opening {} with proxy settings: {}={}, {}={}, http.nonProxyHosts={}", url,
                        proxyHostProperty, proxyHost,
                        proxyPortProperty, proxyPort,
                        nonProxyHosts
                    );
                }
            }
            return url.openConnection();
        } finally {
            GlobalLocks.JUL_INIT_LOCK.unlock();
        }
    }
}
