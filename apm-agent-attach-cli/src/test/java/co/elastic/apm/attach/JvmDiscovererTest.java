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
package co.elastic.apm.attach;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;


class JvmDiscovererTest {

    @Test
    void discoverHotspotJvms() {
        JvmDiscoverer.ForHotSpotVm discoverer = JvmDiscoverer.ForHotSpotVm.withDiscoveredTempDirs(UserRegistry.empty());
        assertThat(discoverer.isAvailable())
            .describedAs("HotSpot JVM discovery should be available")
            .isTrue();
        assertThat(discoverer.discoverJvms().stream().map(JvmInfo::getPid)).contains(JvmInfo.CURRENT_PID);
    }

    @Test
    void testPsDiscovererAvailableOnJ9() throws Exception {
        Assumptions.assumeTrue(JvmInfo.isJ9());
        JvmDiscoverer.UsingPs usingPs = new JvmDiscoverer.UsingPs(UserRegistry.empty());
        assertThat(usingPs.isAvailable()).isTrue();
        assertThat(usingPs.discoverJvms().stream().map(JvmInfo::getPid)).contains(String.valueOf(ProcessHandle.current().pid()));
    }

    @Test
    void testPsDiscovererNotAvailableOnHotspot() {
        assumeFalse(JvmInfo.isJ9());
        JvmDiscoverer.UsingPs usingPs = new JvmDiscoverer.UsingPs(UserRegistry.empty());
        assertThat(usingPs.isAvailable()).isFalse();
    }
}
