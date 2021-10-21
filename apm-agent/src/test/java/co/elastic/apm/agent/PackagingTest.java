package co.elastic.apm.agent;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingTest {

    @Test
    void checkPluginDependencies() throws Exception {
        // search for all known META-INF/services files

        Path classLocation = Paths.get(PackagingTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        Path moduleRoot = classLocation.getParent().getParent();
        assertThat(moduleRoot).isDirectory();

        // compute the list of all plugin classes by scanning service loader files from filesystem
        Path pluginsModulePath = moduleRoot.getParent().resolve("apm-agent-plugins");
        Set<Path> serviceLoaderFiles = Files.find(pluginsModulePath, 10, (path, attributes) -> {
                Path fileFolder = path.getParent();
                return Files.isRegularFile(path) && fileFolder.endsWith(Path.of("src", "main", "resources", "META-INF", "services"));
            })
            .collect(Collectors.toSet());

        Set<String> pluginArtifacts = new HashSet<>();
        serviceLoaderFiles.forEach(path -> {
            Path pluginRoot = path.getParent().getParent().getParent().getParent().getParent().getParent();
            String pluginArtifact = getArtifactId(pluginRoot.resolve("pom.xml"));
            pluginArtifacts.add(pluginArtifact);
        });

        assertThat(pluginArtifacts).isNotEmpty();

        checkContainsPluginAsDependencies(moduleRoot.resolve("pom.xml"), pluginArtifacts);
    }

    private static String getArtifactId(Path pomPath) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(Files.newBufferedReader(pomPath));
            return model.getArtifactId();
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void checkContainsPluginAsDependencies(Path pomPath, Set<String> plugins) {
        assertThat(pomPath).isRegularFile();

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(Files.newBufferedReader(pomPath));
            Set<String> dependenciesArtifacts = model.getDependencies().stream()
                .filter(d -> sameProjectGroup(model, d) && sameProjectVersion(model, d))
                .map(Dependency::getArtifactId)
                .collect(Collectors.toSet());

            assertThat(dependenciesArtifacts).describedAs(createDescription(plugins)).containsAll(plugins);
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }

    }

    private static String createDescription(Set<String> plugins) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n fix apm-agent dependencies with following snippet:\n");

        ArrayList<String> list = new ArrayList<>(plugins);
        list.sort(Comparator.naturalOrder());
        for (String s : list) {
            sb.append("<dependency>\n")
                .append("    <groupId>${project.groupId}</groupId>\n")
                .append("    <artifactId>").append(s).append("</artifactId>\n")
                .append("    <version>${project.version}</version>\n")
                .append("</dependency>\n");
        }

        return sb.toString();
    }

    private static boolean sameProjectVersion(Model model, Dependency d) {
        String value = d.getVersion();
        return value.equals(model.getVersion()) || value.equals("${project.version}");
    }

    private static boolean sameProjectGroup(Model model, Dependency d) {
        String value = d.getGroupId();
        return value.equals(model.getGroupId()) || value.equals("${project.groupId}");
    }
}
