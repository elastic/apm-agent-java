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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegistryTest {

    @Test
    @EnabledOnOs(OS.MAC)
    void testGetAllUsersMacOS() throws Exception {
        assertThat(UserRegistry.getAllUsersMacOs().getAllUserNames()).contains("root", System.getProperty("user.name"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testCannotSwitchToRoot() {
        // In GitHub Action, it's possible to perform a privilege escalation from a non-root user to a root user.
        boolean isInCI = System.getenv("CI") != null;
        Assumptions.assumeTrue(!System.getProperty("user.name").equals("root") && !isInCI);
        assertThat(UserRegistry.empty().get("root").canSwitchToUser()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void testTempDir() throws Exception {
        assertThat(UserRegistry.getAllUsersMacOs().getAllTempDirs()).contains(System.getProperty("java.io.tmpdir"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testCurrentUserCanSwitchToSelf() {
        String userName = System.getProperty("user.name");
        UserRegistry.User user = UserRegistry.empty().get(userName);

        assertThat(user.getUsername()).isEqualTo(userName);
        assertThat(user.isCurrentUser()).isTrue();
        assertThat(user.canSwitchToUser()).isTrue();
    }


}
