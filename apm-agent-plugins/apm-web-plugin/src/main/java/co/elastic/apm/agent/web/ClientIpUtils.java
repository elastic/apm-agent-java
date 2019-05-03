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
package co.elastic.apm.agent.web;

import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;

import javax.annotation.Nullable;

/**
 * Utility class which helps to determine the real IP of a HTTP request
 * <p>
 * This implementation is based on org.stagemonitor.web.servlet.MonitoredHttpRequest#getClientIp,
 * under Apache License 2.0
 * </p>
 */
public class ClientIpUtils {

    /**
     * This method returns the first IP from common HTTP header names used by reverse proxies.
     * If there is no such header, it returns the provided remoteAddr
     *
     * @param headers    the headers of an http request
     * @param remoteAddr the remote address, which could be the IP of a proxy server
     * @return the
     */
    public static String getRealIp(PotentiallyMultiValuedMap headers, String remoteAddr) {
        String ip = headers.getFirst("X-Forwarded-For");
        if (isEmpty(ip)) {
            ip = headers.getFirst("X-Real-IP");
        }
        if (isEmpty(ip)) {
            ip = remoteAddr;
        }
        return getFirstIp(ip);
    }

    private static boolean isEmpty(@Nullable String ip) {
        return ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip);
    }

    /*
     * Can be a comma separated list if there are multiple devices in the forwarding chain
     */
    private static String getFirstIp(String ip) {
        final int indexOfFirstComma = ip.indexOf(',');
        if (indexOfFirstComma != -1) {
            ip = ip.substring(0, indexOfFirstComma);
        }
        return ip;
    }

}
