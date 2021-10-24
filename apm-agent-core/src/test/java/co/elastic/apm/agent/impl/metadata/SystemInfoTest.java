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
package co.elastic.apm.agent.impl.metadata;

import co.elastic.apm.agent.util.CustomEnvVariables;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemInfoTest extends CustomEnvVariables {

    private static final SystemInfo systemInfo;
    private static final boolean isWindows;

    static {
        systemInfo = SystemInfo.create("hostname", 0);
        isWindows = SystemInfo.isWindows(systemInfo.getPlatform());
    }


    @Test
    void testHostnameDiscoveryThroughCommand() {
        String hostname = SystemInfo.discoverHostnameThroughCommand(isWindows, 300);
        assertThat(hostname).isNotNull();
    }

    @Test
    void testHostnameDiscoveryThroughEnv() {
        Map<String, String> customEnvVariables = new HashMap<>();
        if (isWindows) {
            customEnvVariables.put("COMPUTERNAME", "Windows_hostname");
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.discoverHostnameThroughEnv(true)).isEqualTo("Windows_hostname"));
        } else {
            customEnvVariables.put("HOST", "macOS_hostname");
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.discoverHostnameThroughEnv(false)).isEqualTo("macOS_hostname"));
            customEnvVariables.put("HOSTNAME", "Linux_hostname");
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.discoverHostnameThroughEnv(false)).isEqualTo("Linux_hostname"));
        }
    }

    @Test
    void testHostnameDiscoveryFallbackThroughInetAddress() throws UnknownHostException {
        String expectedHostname = InetAddress.getLocalHost().getHostName();

        Map<String, String> customEnvVariables = new HashMap<>();
        if (isWindows) {
            customEnvVariables.put("COMPUTERNAME", null);
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.fallbackHostnameDiscovery(true)).isEqualTo(expectedHostname));
        } else {
            customEnvVariables.put("HOST", null);
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.fallbackHostnameDiscovery(false)).isEqualTo(expectedHostname));
            customEnvVariables.put("HOSTNAME", null);
            runWithCustomEnvVariables(customEnvVariables, () -> assertThat(SystemInfo.fallbackHostnameDiscovery(false)).isEqualTo(expectedHostname));
        }
    }

    @Test
    void testDomainRemoval() {
        assertThat(SystemInfo.removeDomain("hostname")).isEqualTo("hostname");
        assertThat(SystemInfo.removeDomain("hostname.and.domain")).isEqualTo("hostname");
    }
}
