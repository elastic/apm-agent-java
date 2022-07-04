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
package co.elastic.apm.testapp;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AppMainTest {

    @Test
    void checkOldByteBuddyVersion() {
        // this test will prevent dependabot from updating the old version that we politely instructed not to update
        // and will make the PR fail when this happens

        String bytebuddyJar = ByteBuddyAgent.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        assertThat(bytebuddyJar)
            .describedAs("unexpected old version of bytebuddy in test application")
            .endsWith("byte-buddy-agent-1.9.16.jar");
    }

}
