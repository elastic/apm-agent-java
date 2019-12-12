package co.elastic.apm.agent.impl.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackFrameTest {

    @Test
    void testAppendFileName() {
        assertThat(getFileName("Baz")).isEqualTo("Baz.java");
        assertThat(getFileName("foo.bar.Baz")).isEqualTo("Baz.java");
        assertThat(getFileName("foo.bar.Baz$Qux")).isEqualTo("Baz.java");
        assertThat(getFileName("baz")).isEqualTo("baz.java");
    }

    private String getFileName(String classDotMethod) {
        StringBuilder sb = new StringBuilder();
        new StackFrame(classDotMethod, "foo").appendFileName(sb);
        return sb.toString();
    }

}
