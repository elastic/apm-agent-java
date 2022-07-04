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

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir", ""));
        Path rootDir = Path.of("root").toAbsolutePath();

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.log%u",
            tempDir.resolve(Path.of("path","test0.log8")),
            "reformat",
            false)
        ).isEqualTo(tempDir.resolve(Path.of("path", "reformat", "test%g.ecs.json")).toString());

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            // path is always unix-like, equivalent to '/root/path/test%g.log%u' so we have to generate the same on Windows
            rootDir.resolve(Path.of("path","test%g.log%u")).toString().replaceAll("\\\\","/"),
            rootDir.resolve(Path.of("path","test0.log8")),
            "reformat",
            false)
        ).isEqualTo(rootDir.resolve(Path.of("path", "reformat", "test%g.ecs.json")).toString());

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test.log%u",
            tempDir.resolve(Path.of("path","test.log8")),
            "reformat",
            false)
        ).isEqualTo(tempDir.resolve(Path.of("path", "reformat", "test.ecs.json.%g")).toString());

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "test%g.log%u",
            Path.of("test0.log8"),
            "reformat",
            false)
        ).isEqualTo(Path.of("reformat","test%g.ecs.json").toString());

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.%u.log",
            tempDir.resolve(Path.of("path", "test0.8.log")),
            null,
            false)
        ).isEqualTo(tempDir.resolve(Path.of("path", "test%g.%u.ecs.json")).toString());

        assertThat(JulEcsReformattingHelper.computeEcsFileHandlerPattern(
            "%t/test%g.%u.log",
            tempDir.resolve(Path.of("path", "test0.8.log")),
            "",
            false)
        ).isEqualTo(tempDir.resolve(Path.of("path", "test%g.%u.ecs.json")).toString());
    }
}
