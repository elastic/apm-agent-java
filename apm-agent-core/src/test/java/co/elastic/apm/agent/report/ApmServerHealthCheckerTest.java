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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.util.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApmServerHealthCheckerTest {

    @Test
    void testParseVersion() throws IOException {
        // 7.16+
        String body = "{  \"build_date\": \"2021-11-04T12:50:39Z\",  \"build_sha\": \"74a2ccb4be966ebab82b9727caef355fe9097340\",  \"publish_ready\": true,  \"version\": \"8.0.0\"}";
        assertThat(ApmServerHealthChecker.parseVersion(body).compareTo(Version.of("8.0.0"))).isEqualTo(0);
        // 7.0 -> 7.15
        body = "{  \"build_date\": \"2021-03-06T04:41:35Z\",  \"build_sha\": \"b706a93fac838d7ca44622d8d9686d2c3b3c8bde\",  \"version\": \"7.11.2\"}";
        assertThat(ApmServerHealthChecker.parseVersion(body).compareTo(Version.of("7.11.2"))).isEqualTo(0);
        // 6.x
        body = "{\"ok\":{\"build_date\":\"2021-10-13T17:29:41Z\",\"build_sha\":\"04a84d8d3d0358af5e73b3581c4ba37fbdbc979e\",\"version\":\"6.8.20\"}}";
        assertThat(ApmServerHealthChecker.parseVersion(body).compareTo(Version.of("6.8.20"))).isEqualTo(0);
    }
}
