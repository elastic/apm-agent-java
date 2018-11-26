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
package co.elastic.apm.impl.payload;


import java.net.InetAddress;

/**
 * Information about the system the agent is running on.
 */
public class SystemInfo {

    /**
     * Architecture of the system the agent is running on.
     */
    private final String architecture;
    /**
     * Hostname of the system the agent is running on.
     */
    private final String hostname;
    /**
     * Name of the system platform the agent is running on.
     */
    private final String platform;

    public SystemInfo(String architecture, String hostname, String platform) {
        this.architecture = architecture;
        this.hostname = hostname;
        this.platform = platform;
    }

    public static SystemInfo create() {
        return new SystemInfo(System.getProperty("os.arch"), getNameOfLocalHost(), System.getProperty("os.name"));
    }

    static String getNameOfLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return getHostNameFromEnv();
        }
    }

    private static String getHostNameFromEnv() {
        // try environment properties.
        String host = System.getenv("COMPUTERNAME");
        if (host == null) {
            host = System.getenv("HOSTNAME");
        }
        if (host == null) {
            host = System.getenv("HOST");
        }
        return host;
    }

    /**
     * Architecture of the system the agent is running on.
     */
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Hostname of the system the agent is running on.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    public String getPlatform() {
        return platform;
    }

}
