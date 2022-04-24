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
