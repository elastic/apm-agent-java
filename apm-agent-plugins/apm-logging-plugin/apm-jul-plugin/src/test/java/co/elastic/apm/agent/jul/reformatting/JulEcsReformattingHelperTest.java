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
package co.elastic.apm.agent.jul.reformatting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JulEcsReformattingHelperTest {

    @Test
    void testEcsFileHandlerPatternComputation() throws IOException {

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.log%u",
            Path.of("/tmp/path/test0.log8"),
            "reformat",
            false)
        ).isEqualTo("/tmp/path/reformat/test%g.ecs.json");

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "/root/path/test%g.log%u",
            Path.of("/root/path/test0.log8"),
            "reformat",
            false)
        ).isEqualTo("/root/path/reformat/test%g.ecs.json");

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test.log%u",
            Path.of("/tmp/path/test.log8"),
            "reformat",
            false)
        ).isEqualTo("/tmp/path/reformat/test.ecs.json.%g");

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "test%g.log%u",
            Path.of("test0.log8"),
            "reformat",
            false)
        ).isEqualTo("reformat/test%g.ecs.json");

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.%u.log",
            Path.of("/tmp/path/test0.8.log"),
            null,
            false)
        ).isEqualTo("/tmp/path/test%g.%u.ecs.json");

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.%u.log",
            Path.of("/tmp/path/test0.8.log"),
            "",
            false)
        ).isEqualTo("/tmp/path/test%g.%u.ecs.json");
    }
}
