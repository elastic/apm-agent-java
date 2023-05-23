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
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public class UrlConnectionUtils {

    private static final Logger logger = LoggerFactory.getLogger(UrlConnectionUtils.class);

    public static URLConnection openUrlConnectionThreadSafely(URL url, boolean allowProxy) throws IOException {
        GlobalLocks.JUL_INIT_LOCK.lock();
        try {
            if (logger.isDebugEnabled()) {
                debugPrintProxySettings(url, allowProxy);
            }
            if (allowProxy) {
                return url.openConnection();
            } else {
                return url.openConnection(Proxy.NO_PROXY);
            }
        } finally {
            GlobalLocks.JUL_INIT_LOCK.unlock();
        }
    }

    private static void debugPrintProxySettings(URL url, boolean allowProxy) {
        if (!allowProxy) {
            logger.debug("Opening {} without proxy", url);
            return;
        }

        ProxySelector proxySelector = PrivilegedActionUtils.getDefaultProxySelector();
        if (proxySelector == null || proxySelector.getClass().getName().equals("sun.net.spi.DefaultProxySelector")) {
            String proxyHostProperty = url.getProtocol() + ".proxyHost";
            String proxyPortProperty = url.getProtocol() + ".proxyPort";
            String proxyHost = PrivilegedActionUtils.getProperty(proxyHostProperty);
            String proxyPort = PrivilegedActionUtils.getProperty(proxyPortProperty);
            String nonProxyHosts = PrivilegedActionUtils.getProperty("http.nonProxyHosts"); // common to http & https
            if (proxyHost == null || proxyHost.isEmpty()) {
                logger.debug("Opening {} without proxy", url);
            } else {
                logger.debug("Opening {} with proxy settings: {}={}, {}={}, http.nonProxyHosts={}", url,
                    proxyHostProperty, proxyHost,
                    proxyPortProperty, proxyPort,
                    nonProxyHosts
                );
            }
        } else {
            try {
                List<Proxy> proxies = proxySelector.select(url.toURI());
                String proxySelectorName = proxySelector.getClass().getName();
                if (proxies != null && proxies.size() == 1 && proxies.get(0).equals(Proxy.NO_PROXY)) {
                    logger.debug("Opening {} without proxy (ProxySelector {})", url, proxySelectorName);
                } else {
                    logger.debug("Opening {} with proxies {} (ProxySelector {})", url, proxies, proxySelectorName);
                }
            } catch (URISyntaxException e) {
                logger.debug("Failed to read and debug-print proxy settings for {}", url, e);
            }
        }
    }
}
