package co.elastic.apm.servlet;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentFileIT {

    static String getPathToJavaagent() {
        File agentBuildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = new WildcardFileFilter("elastic-apm-agent-*.jar");
        for (File file : agentBuildDir.listFiles(fileFilter)) {
            if (!file.getAbsolutePath().endsWith("javadoc.jar") && !file.getAbsolutePath().endsWith("sources.jar")) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    @Test
    void testEverythingIsShaded() throws IOException {
        final String pathToJavaagent = getPathToJavaagent();
        assertThat(pathToJavaagent).isNotNull();
        try (JarFile agentJar = new JarFile(new File(pathToJavaagent))) {
            assertThat(
                agentJar.stream()
                    .map(JarEntry::getName)
                    .filter(entry -> !entry.startsWith("META-INF/"))
                    .filter(entry -> !entry.startsWith("co/"))
                    .filter(entry -> !entry.startsWith("schema/")))
                .isEmpty();
        }
    }
}
